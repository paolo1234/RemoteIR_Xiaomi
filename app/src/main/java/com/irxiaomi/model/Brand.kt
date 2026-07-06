package com.irxiaomi.model

/**
 * Marca del dispositivo
 */
data class Brand(
    val id: Long = 0,
    val name: String,
    val aliases: List<String> = emptyList(),
    val country: String = "",
    val logoUrl: String = "",
    val deviceTypes: List<DeviceType> = DeviceType.entries,
    val codeCount: Int = 0,
    val popularModels: List<String> = emptyList()
) {
    companion object {
        /** Marche predefinite con popolarità */
        val PRESET = listOf(
            Brand(1, "Samsung", listOf("Samsung Electronics"), "Corea"),
            Brand(2, "LG", listOf("LG Electronics", "GoldStar"), "Corea"),
            Brand(3, "Sony", listOf("Sony Corporation"), "Giappone"),
            Brand(4, "Panasonic", listOf("Matsushita"), "Giappone"),
            Brand(5, "Philips", listOf("Philips Electronics"), "Paesi Bassi"),
            Brand(6, "Daikin", listOf(), "Giappone", deviceTypes = listOf(DeviceType.AC)),
            Brand(7, "Mitsubishi", listOf("Mitsubishi Electric"), "Giappone"),
            Brand(8, "Sharp", listOf(), "Giappone"),
            Brand(9, "Toshiba", listOf(), "Giappone"),
            Brand(10, "Hitachi", listOf(), "Giappone"),
            Brand(11, "Xiaomi", listOf("Mi", "Redmi"), "Cina"),
            Brand(12, "Hisense", listOf(), "Cina"),
            Brand(13, "TCL", listOf(), "Cina"),
            Brand(14, "Haier", listOf(), "Cina"),
            Brand(15, "Gree", listOf(), "Cina", deviceTypes = listOf(DeviceType.AC)),
            Brand(16, "Midea", listOf(), "Cina", deviceTypes = listOf(DeviceType.AC)),
            Brand(17, "Electrolux", listOf(), "Svezia"),
            Brand(18, "Whirlpool", listOf(), "USA"),
            Brand(19, "Bose", listOf(), "USA", deviceTypes = listOf(DeviceType.AUDIO)),
            Brand(20, "Harman/Kardon", listOf(), "USA", deviceTypes = listOf(DeviceType.AUDIO)),
            Brand(21, "Yamaha", listOf(), "Giappone", deviceTypes = listOf(DeviceType.AUDIO)),
            Brand(22, "Denon", listOf(), "Giappone", deviceTypes = listOf(DeviceType.AUDIO)),
            Brand(23, "Onkyo", listOf(), "Giappone", deviceTypes = listOf(DeviceType.AUDIO)),
            Brand(24, "Marantz", listOf(), "Giappone", deviceTypes = listOf(DeviceType.AUDIO)),
            Brand(25, "Apple", listOf(), "USA"),
            Brand(26, "Google", listOf(), "USA"),
            Brand(27, "Amazon", listOf(), "USA"),
            Brand(28, "Nvidia", listOf(), "USA"),
            Brand(29, "Roku", listOf(), "USA"),
            Brand(30, "ZTE", listOf(), "Cina"),
            Brand(31, "Huawei", listOf(), "Cina"),
            Brand(32, "Lenovo", listOf(), "Cina"),
            Brand(33, "Acer", listOf(), "Taiwan"),
            Brand(34, "BenQ", listOf(), "Taiwan"),
            Brand(35, "Epson", listOf(), "Giappone", deviceTypes = listOf(DeviceType.PROJECTOR)),
            Brand(36, "Optoma", listOf(), "Taiwan", deviceTypes = listOf(DeviceType.PROJECTOR)),
            Brand(37, "Vizio", listOf(), "USA"),
            Brand(38, "Sky", listOf(), "UK", deviceTypes = listOf(DeviceType.SET_TOP_BOX)),
            Brand(39, "BT", listOf(), "UK", deviceTypes = listOf(DeviceType.SET_TOP_BOX)),
            Brand(40, "Tivùsat", listOf(), "Italia", deviceTypes = listOf(DeviceType.SET_TOP_BOX)),
        )

        private val nameToBrand = PRESET.associateBy { it.name.lowercase() }

        fun fromName(name: String): Brand? {
            val lower = name.lowercase().trim()
            return nameToBrand[lower]
                ?: nameToBrand.entries.find { (_, brand) ->
                    brand.aliases.any { it.lowercase() == lower }
                }?.value
                ?: PRESET.find { lower.contains(it.name.lowercase()) }
        }

        fun search(query: String): List<Brand> {
            val q = query.lowercase()
            return PRESET.filter {
                it.name.lowercase().contains(q) ||
                it.aliases.any { a -> a.lowercase().contains(q) }
            }
        }
    }
}
