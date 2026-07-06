"""
IRXiaomi - Modelli SQLAlchemy per il Sync Server.
Versione standalone importabile anche da script esterni.
"""

import uuid
from datetime import datetime
from sqlalchemy import (
    Column, Integer, BigInteger, String, Boolean,
    Float, DateTime, Text, Index, create_engine
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

Base = declarative_base()


class IrCodeDB(Base):
    """Codice IR universale."""
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
    """Registro sincronizzazioni."""
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
    """Token API."""
    __tablename__ = "api_tokens"

    id = Column(Integer, primary_key=True, autoincrement=True)
    token = Column(String(64), unique=True, index=True)
    device_id = Column(String(100), index=True)
    is_active = Column(Boolean, default=True)
    rate_limit = Column(Integer, default=100)
    created_at = Column(DateTime, default=datetime.utcnow)
    last_used_at = Column(DateTime, nullable=True)


def init_db(database_url: str = "postgresql://irxiaomi:irxiaomi_pass@localhost:5432/irxiaomi"):
    """Inizializza il database e crea le tabelle."""
    engine = create_engine(database_url)
    Base.metadata.create_all(bind=engine)
    return engine


def get_session(engine):
    """Crea una sessione SQLAlchemy."""
    Session = sessionmaker(bind=engine)
    return Session()
