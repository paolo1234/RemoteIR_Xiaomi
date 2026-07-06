"""
IRXiaomi Sync Server
FastAPI + SQLAlchemy + PostgreSQL
Database remoto per codici IR, condiviso tra tutti gli utenti dell'app.

API Endpoints:
- GET  /api/health                           → Status server
- GET  /api/stats                            → Statistiche database
- GET  /api/brands                           → Lista marche
- GET  /api/codes/search                     → Ricerca codici
- GET  /api/codes/{id}                       → Dettaglio codice
- POST /api/codes                            → Upload singolo codice
- POST /api/codes/bulk                       → Upload multiplo (batch)
- POST /api/codes/{id}/vote                  → Vota codice
- GET  /api/codes/popular                    → Codici più votati
- GET  /api/codes/recent                     → Codici recenti
- GET  /api/codes/by-brand/{brand}           → Codici per marca
- GET  /api/codes/by-device/{device_type}    → Codici per tipo dispositivo
"""

import os
import uuid
from datetime import datetime
from typing import List, Optional

from fastapi import FastAPI, Depends, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from sqlalchemy import (
    create_engine, Column, Integer, BigInteger, String, Boolean,
    Float, DateTime, Text, Index, func, desc, or_, and_
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import Session, sessionmaker

# =============================================================================
# Configurazione
# =============================================================================

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://irxiaomi:irxiaomi_pass@localhost:5432/irxiaomi"
)
SYNC_INTERVAL_MINUTES = int(os.getenv("SYNC_INTERVAL_MINUTES", "60"))
MAX_BATCH_SIZE = int(os.getenv("MAX_BATCH_SIZE", "500"))
API_RATE_LIMIT = int(os.getenv("API_RATE_LIMIT", "100"))  # richieste/minuto

engine = create_engine(DATABASE_URL, pool_pre_ping=True, pool_size=10, max_overflow=20)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

app = FastAPI(
    title="IRXiaomi Sync Server",
    description="Database remoto per codici IR universali",
    version="1.0.0",
    docs_url="/api/docs",
    redoc_url="/api/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# =============================================================================
# Modelli SQLAlchemy
# =============================================================================

class IrCodeDB(Base):
    __tablename__ = "ir_codes"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    uuid = Column(String(36), unique=True, default=lambda: str(uuid.uuid4()), index=True)

    # Identificazione
    name = Column(String(255), nullable=False, index=True)
    display_name = Column(String(255), default="")
    brand = Column(String(255), nullable=False, index=True)
    model = Column(String(255), default="", index=True)
    device_type = Column(String(100), nullable=False, index=True)

    # Segnale IR
    protocol = Column(String(50), nullable=False, default="NEC", index=True)
    frequency = Column(Integer, nullable=False, default=38000)
    pattern = Column(Text, nullable=False, default="")
    address = Column(BigInteger, nullable=True)
    command = Column(BigInteger, nullable=True)

    # Classificazione
    category = Column(String(255), default="")
    tags = Column(Text, default="")

    # Provenienza
    source = Column(String(50), default="user_uploaded")
    is_verified = Column(Boolean, default=False)
    is_official = Column(Boolean, default=False)

    # Valutazione
    rating = Column(Float, default=0.0)
    vote_count = Column(Integer, default=0)
    download_count = Column(Integer, default=0)

    # Metadati
    notes = Column(Text, default="")
    contributor = Column(String(255), default="anonymous")
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Indici composti per ricerca veloce
    __table_args__ = (
        Index("idx_brand_device", "brand", "device_type"),
        Index("idx_brand_model", "brand", "model"),
        Index("idx_protocol_addr_cmd", "protocol", "address", "command"),
        Index("idx_source_verified", "source", "is_verified"),
    )

    def to_dict(self):
        return {
            "id": self.id,
            "uuid": self.uuid,
            "name": self.name,
            "display_name": self.display_name or self.name,
            "brand": self.brand,
            "model": self.model,
            "device_type": self.device_type,
            "protocol": self.protocol,
            "frequency": self.frequency,
            "pattern": self.pattern,
            "address": self.address,
            "command": self.command,
            "category": self.category,
            "tags": self.tags,
            "source": self.source,
            "is_verified": self.is_verified,
            "is_official": self.is_official,
            "rating": self.rating,
            "vote_count": self.vote_count,
            "download_count": self.download_count,
            "contributor": self.contributor,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }


class SyncLog(Base):
    """Registro delle sincronizzazioni per tracciare cambiamenti."""
    __tablename__ = "sync_logs"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    device_id = Column(String(100), index=True)
    action = Column(String(50))  # "upload", "download", "sync"
    code_id = Column(BigInteger, nullable=True)
    status = Column(String(20))  # "success", "error"
    message = Column(Text, default="")
    ip_address = Column(String(45), default="")
    created_at = Column(DateTime, default=datetime.utcnow)


class ApiToken(Base):
    """Token API per autenticazione opzionale."""
    __tablename__ = "api_tokens"

    id = Column(Integer, primary_key=True, autoincrement=True)
    token = Column(String(64), unique=True, index=True)
    device_id = Column(String(100), index=True)
    is_active = Column(Boolean, default=True)
    rate_limit = Column(Integer, default=100)
    created_at = Column(DateTime, default=datetime.utcnow)
    last_used_at = Column(DateTime, nullable=True)


# =============================================================================
# Schemi Pydantic per Request/Response
# =============================================================================

class IrCodeCreate(BaseModel):
    name: str
    display_name: Optional[str] = None
    brand: str
    model: Optional[str] = ""
    device_type: str = "OTHER"
    protocol: str = "NEC"
    frequency: int = 38000
    pattern: str = ""
    address: Optional[int] = None
    command: Optional[int] = None
    category: Optional[str] = ""
    tags: Optional[str] = ""
    source: str = "user_uploaded"
    notes: Optional[str] = ""
    contributor: Optional[str] = "anonymous"


class IrCodeBulkCreate(BaseModel):
    codes: List[IrCodeCreate]
    contributor: Optional[str] = "anonymous"


class VoteRequest(BaseModel):
    rating: float = Field(..., ge=0.5, le=5.0)
    device_id: Optional[str] = None


class SyncRequest(BaseModel):
    device_id: str
    last_sync_at: Optional[str] = None
    local_codes: Optional[List[dict]] = None


class SearchParams(BaseModel):
    brand: Optional[str] = None
    device_type: Optional[str] = None
    model: Optional[str] = None
    protocol: Optional[str] = None
    query: Optional[str] = None
    limit: int = 50
    offset: int = 0


# =============================================================================
# Dipendenze
# =============================================================================

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def log_sync(
    db: Session,
    device_id: str,
    action: str,
    status: str = "success",
    code_id: int = None,
    message: str = "",
    ip: str = ""
):
    log = SyncLog(
        device_id=device_id,
        action=action,
        code_id=code_id,
        status=status,
        message=message,
        ip_address=ip
    )
    db.add(log)
    db.commit()


# =============================================================================
# Endpoints API
# =============================================================================

# ---- Health & Stats ----

@app.get("/api/health")
def health():
    return {
        "status": "ok",
        "version": "1.0.0",
        "timestamp": datetime.utcnow().isoformat()
    }


@app.get("/api/stats")
def get_stats(db: Session = Depends(get_db)):
    total = db.query(func.count(IrCodeDB.id)).scalar() or 0
    verified = db.query(func.count(IrCodeDB.id)).filter(IrCodeDB.is_verified.is_(True)).scalar() or 0
    brands = db.query(IrCodeDB.brand).distinct().count()
    device_types = db.query(IrCodeDB.device_type).distinct().count()
    protocols = db.query(IrCodeDB.protocol).distinct().count()
    top_brands = (
        db.query(IrCodeDB.brand, func.count(IrCodeDB.id).label("cnt"))
        .group_by(IrCodeDB.brand)
        .order_by(desc("cnt"))
        .limit(10)
        .all()
    )

    return {
        "total_codes": total,
        "verified_codes": verified,
        "total_brands": brands,
        "total_device_types": device_types,
        "total_protocols": protocols,
        "top_brands": [{"brand": b, "count": c} for b, c in top_brands],
        "server_time": datetime.utcnow().isoformat(),
        "db_size_mb": get_db_size(),
    }


def get_db_size() -> float:
    """Stima la dimensione del database in MB."""
    try:
        result = engine.execute(
            "SELECT pg_database_size(current_database()) / 1048576.0"
        ).scalar()
        return round(float(result), 2) if result else 0.0
    except Exception:
        return 0.0


# ---- Brands ----

@app.get("/api/brands")
def get_brands(
    search: Optional[str] = None,
    device_type: Optional[str] = None,
    db: Session = Depends(get_db)
):
    query = db.query(
        IrCodeDB.brand,
        func.count(IrCodeDB.id).label("code_count")
    )

    if device_type:
        query = query.filter(IrCodeDB.device_type == device_type)

    if search:
        query = query.filter(IrCodeDB.brand.ilike(f"%{search}%"))

    query = query.group_by(IrCodeDB.brand).order_by(desc("code_count"))

    return [{"brand": b, "code_count": c} for b, c in query.all()]


# ---- Codes ----

@app.get("/api/codes/search")
def search_codes(
    brand: Optional[str] = None,
    device_type: Optional[str] = None,
    model: Optional[str] = None,
    protocol: Optional[str] = None,
    query: Optional[str] = None,
    limit: int = Query(50, ge=1, le=500),
    offset: int = Query(0, ge=0),
    verified_only: bool = False,
    db: Session = Depends(get_db)
):
    q = db.query(IrCodeDB)

    if brand:
        # Supporto per marche multiple separate da virgola
        brands = [b.strip() for b in brand.split(",")]
        q = q.filter(IrCodeDB.brand.in_(brands))

    if device_type:
        q = q.filter(IrCodeDB.device_type == device_type)

    if model:
        q = q.filter(IrCodeDB.model.ilike(f"%{model}%"))

    if protocol:
        q = q.filter(IrCodeDB.protocol == protocol)

    if query:
        search_filter = or_(
            IrCodeDB.name.ilike(f"%{query}%"),
            IrCodeDB.display_name.ilike(f"%{query}%"),
            IrCodeDB.brand.ilike(f"%{query}%"),
            IrCodeDB.model.ilike(f"%{query}%"),
            IrCodeDB.tags.ilike(f"%{query}%"),
            IrCodeDB.category.ilike(f"%{query}%"),
        )
        q = q.filter(search_filter)

    if verified_only:
        q = q.filter(IrCodeDB.is_verified.is_(True))

    total = q.count()
    results = q.order_by(desc(IrCodeDB.rating), desc(IrCodeDB.download_count)) \
               .offset(offset).limit(limit).all()

    return {
        "total": total,
        "limit": limit,
        "offset": offset,
        "results": [r.to_dict() for r in results]
    }


@app.get("/api/codes/popular")
def get_popular(
    limit: int = Query(100, ge=1, le=1000),
    min_rating: float = Query(0.0, ge=0.0, le=5.0),
    db: Session = Depends(get_db)
):
    results = (
        db.query(IrCodeDB)
        .filter(IrCodeDB.rating >= min_rating)
        .order_by(desc(IrCodeDB.download_count), desc(IrCodeDB.rating))
        .limit(limit)
        .all()
    )
    return [r.to_dict() for r in results]


@app.get("/api/codes/recent")
def get_recent(
    limit: int = Query(50, ge=1, le=500),
    db: Session = Depends(get_db)
):
    results = (
        db.query(IrCodeDB)
        .order_by(desc(IrCodeDB.created_at))
        .limit(limit)
        .all()
    )
    return [r.to_dict() for r in results]


@app.get("/api/codes/{code_id}")
def get_code(code_id: int, db: Session = Depends(get_db)):
    code = db.query(IrCodeDB).filter(IrCodeDB.id == code_id).first()
    if not code:
        raise HTTPException(status_code=404, detail="Codice non trovato")
    return code.to_dict()


@app.get("/api/codes/by-uuid/{uuid}")
def get_code_by_uuid(uuid: str, db: Session = Depends(get_db)):
    code = db.query(IrCodeDB).filter(IrCodeDB.uuid == uuid).first()
    if not code:
        raise HTTPException(status_code=404, detail="Codice non trovato")
    return code.to_dict()


@app.post("/api/codes")
def create_code(
    code_data: IrCodeCreate,
    contributor: Optional[str] = "anonymous",
    db: Session = Depends(get_db)
):
    # Verifica duplicati approssimativi (stesso brand, device_type, protocol, address, command)
    if code_data.address is not None and code_data.command is not None:
        existing = (
            db.query(IrCodeDB)
            .filter(
                IrCodeDB.brand == code_data.brand,
                IrCodeDB.device_type == code_data.device_type,
                IrCodeDB.protocol == code_data.protocol,
                IrCodeDB.address == code_data.address,
                IrCodeDB.command == code_data.command,
            )
            .first()
        )
        if existing:
            raise HTTPException(
                status_code=409,
                detail=f"Codice già esistente (id={existing.id})"
            )

    code = IrCodeDB(
        uuid=str(uuid.uuid4()),
        name=code_data.name,
        display_name=code_data.display_name or code_data.name,
        brand=code_data.brand,
        model=code_data.model,
        device_type=code_data.device_type,
        protocol=code_data.protocol,
        frequency=code_data.frequency,
        pattern=code_data.pattern,
        address=code_data.address,
        command=code_data.command,
        category=code_data.category or f"{code_data.brand}-{code_data.device_type}",
        tags=code_data.tags or f"{code_data.brand},{code_data.device_type},{code_data.protocol}",
        source=code_data.source,
        contributor=contributor or code_data.contributor,
        notes=code_data.notes,
        created_at=datetime.utcnow(),
    )
    db.add(code)
    db.commit()
    db.refresh(code)

    return code.to_dict()


@app.post("/api/codes/bulk")
def create_codes_bulk(
    bulk_data: IrCodeBulkCreate,
    db: Session = Depends(get_db)
):
    if len(bulk_data.codes) > MAX_BATCH_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"Massimo {MAX_BATCH_SIZE} codici per richiesta"
        )

    created = []
    errors = []

    for i, code_data in enumerate(bulk_data.codes):
        try:
            code = IrCodeDB(
                uuid=str(uuid.uuid4()),
                name=code_data.name,
                display_name=code_data.display_name or code_data.name,
                brand=code_data.brand,
                model=code_data.model or "",
                device_type=code_data.device_type,
                protocol=code_data.protocol,
                frequency=code_data.frequency,
                pattern=code_data.pattern,
                address=code_data.address,
                command=code_data.command,
                category=code_data.category or f"{code_data.brand}-{code_data.device_type}",
                tags=code_data.tags or f"{code_data.brand},{code_data.device_type},{code_data.protocol}",
                source=code_data.source,
                contributor=bulk_data.contributor or "anonymous",
                notes=code_data.notes or "",
                created_at=datetime.utcnow(),
            )
            db.add(code)
            db.flush()
            created.append(code.to_dict())
        except Exception as e:
            db.rollback()
            errors.append({"index": i, "error": str(e)})
            # Ricomincia transazione
            db = SessionLocal()

    db.commit()

    return {
        "total": len(bulk_data.codes),
        "created": len(created),
        "errors": len(errors),
        "codes": created,
        "error_details": errors[:10],  # Primi 10 errori
    }


@app.post("/api/codes/{code_id}/vote")
def vote_code(
    code_id: int,
    vote: VoteRequest,
    db: Session = Depends(get_db)
):
    code = db.query(IrCodeDB).filter(IrCodeDB.id == code_id).first()
    if not code:
        raise HTTPException(status_code=404, detail="Codice non trovato")

    # Media ponderata
    old_total = code.rating * code.vote_count
    code.vote_count += 1
    code.rating = round((old_total + vote.rating) / code.vote_count, 2)

    db.commit()
    return {
        "id": code.id,
        "new_rating": code.rating,
        "vote_count": code.vote_count
    }


# ---- Sync ----

@app.post("/api/sync")
def sync_device(
    sync_data: SyncRequest,
    db: Session = Depends(get_db),
    x_forwarded_for: Optional[str] = None
):
    """
    Endpoint di sincronizzazione per dispositivi.
    L'app invia i propri codici locali e riceve quelli nuovi dal server.
    """
    ip = x_forwarded_for or "unknown"
    response = {
        "server_time": datetime.utcnow().isoformat(),
        "new_codes": [],
        "updated_codes": [],
        "deleted_ids": [],
    }

    try:
        # Se l'app invia i suoi codici locali, controlla se ci sono nuovi
        if sync_data.local_codes:
            for local_code in sync_data.local_codes:
                # Cerca matching per uuid o per address+command
                existing = None
                if "uuid" in local_code and local_code["uuid"]:
                    existing = db.query(IrCodeDB).filter(
                        IrCodeDB.uuid == local_code["uuid"]
                    ).first()

                if not existing and "address" in local_code and "command" in local_code:
                    existing = db.query(IrCodeDB).filter(
                        IrCodeDB.brand == local_code.get("brand", ""),
                        IrCodeDB.protocol == local_code.get("protocol", "NEC"),
                        IrCodeDB.address == local_code.get("address"),
                        IrCodeDB.command == local_code.get("command"),
                    ).first()

                if not existing:
                    # Nuovo codice dall'app
                    try:
                        code = IrCodeDB(
                            uuid=local_code.get("uuid", str(uuid.uuid4())),
                            name=local_code.get("name", "Unknown"),
                            display_name=local_code.get("display_name", ""),
                            brand=local_code.get("brand", "Unknown"),
                            model=local_code.get("model", ""),
                            device_type=local_code.get("device_type", "OTHER"),
                            protocol=local_code.get("protocol", "NEC"),
                            frequency=local_code.get("frequency", 38000),
                            pattern=local_code.get("pattern", ""),
                            address=local_code.get("address"),
                            command=local_code.get("command"),
                            source="user_synced",
                        )
                        db.add(code)
                        db.flush()
                        response["new_codes"].append(code.to_dict())
                    except Exception:
                        pass

        # Recupera codici nuovi (ultima ora)
        since = datetime.utcnow()
        if sync_data.last_sync_at:
            try:
                since = datetime.fromisoformat(sync_data.last_sync_at)
            except ValueError:
                pass

        new_remote = (
            db.query(IrCodeDB)
            .filter(IrCodeDB.created_at > since)
            .order_by(IrCodeDB.created_at.desc())
            .limit(200)
            .all()
        )
        response["new_codes"].extend([c.to_dict() for c in new_remote])

        log_sync(db, sync_data.device_id, "sync", ip=ip)
        response["status"] = "success"

    except Exception as e:
        db.rollback()
        log_sync(db, sync_data.device_id, "sync", "error", message=str(e), ip=ip)
        response["status"] = "error"
        response["message"] = str(e)

    return response


# ---- Admin (senza auth per MVP - in produzione aggiungere API key) ----

@app.delete("/api/admin/codes/{code_id}")
def admin_delete_code(
    code_id: int,
    confirm: bool = Query(False),
    db: Session = Depends(get_db)
):
    if not confirm:
        raise HTTPException(status_code=400, detail="Conferma richiesta (?confirm=true)")

    code = db.query(IrCodeDB).filter(IrCodeDB.id == code_id).first()
    if not code:
        raise HTTPException(status_code=404, detail="Codice non trovato")

    db.delete(code)
    db.commit()
    return {"status": "deleted", "id": code_id}


@app.get("/api/admin/stats/daily")
def daily_stats(db: Session = Depends(get_db)):
    """Statistiche giornaliere (ultimi 30 giorni)."""
    from sqlalchemy import cast, Date

    daily = (
        db.query(
            cast(IrCodeDB.created_at, Date).label("date"),
            func.count(IrCodeDB.id).label("count")
        )
        .group_by(cast(IrCodeDB.created_at, Date))
        .order_by(desc("date"))
        .limit(30)
        .all()
    )

    return [{"date": str(d), "count": c} for d, c in daily]


@app.get("/api/admin/top-contributors")
def top_contributors(limit: int = 20, db: Session = Depends(get_db)):
    results = (
        db.query(
            IrCodeDB.contributor,
            func.count(IrCodeDB.id).label("count")
        )
        .group_by(IrCodeDB.contributor)
        .order_by(desc("count"))
        .limit(limit)
        .all()
    )
    return [{"contributor": c, "count": cnt} for c, cnt in results]


# =============================================================================
# Avvio
# =============================================================================

@app.on_event("startup")
def startup():
    """Crea le tabelle all'avvio (solo per sviluppo - in produzione usare migration)."""
    Base.metadata.create_all(bind=engine)
    print(f"✅ Database inizializzato: {DATABASE_URL}")
    print(f"   Tabelle: {', '.join(Base.metadata.tables.keys())}")


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", "8000"))
    host = os.getenv("HOST", "0.0.0.0")
    print(f"🚀 IRXiaomi Sync Server avviato su http://{host}:{port}")
    print(f"📚 API Docs: http://{host}:{port}/api/docs")
    uvicorn.run("main:app", host=host, port=port, reload=True)
