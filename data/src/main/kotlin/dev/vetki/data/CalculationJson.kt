package dev.vetki.data

import org.json.JSONObject

data class CalculationDraft(
    val mode: String = "basic",
    val operation: String = "amount",
    val expression: String = "",
    val base: String = "",
    val rate: String = "",
    val monthly: Boolean = false,
    val pay: String = "",
    val worked: String = "",
    val planned: String = "",
    val bonus: String = "",
    val deduction: String = "",
)

object CalculationJson {
    fun encode(d: CalculationDraft): String =
        JSONObject()
            .put("mode", d.mode)
            .put("operation", d.operation)
            .put("expression", d.expression)
            .put("base", d.base)
            .put("rate", d.rate)
            .put("monthly", d.monthly)
            .put("pay", d.pay)
            .put("worked", d.worked)
            .put("planned", d.planned)
            .put("bonus", d.bonus)
            .put("deduction", d.deduction)
            .toString()

    fun decode(payload: String?): CalculationDraft {
        if (payload.isNullOrBlank()) return CalculationDraft()
        require(payload.length <= 4096)
        val o = JSONObject(payload)
        val mode = o.optString("mode", "basic")
        val operation = o.optString("operation", "amount")
        require(mode in setOf("basic", "percent", "salary"))
        require(operation in setOf("amount", "discount", "increase", "ratio"))
        fun field(name: String, max: Int = 80): String =
            o.optString(name).also { require(it.length <= max) }
        return CalculationDraft(
            mode,
            operation,
            field("expression", 512),
            field("base"),
            field("rate"),
            o.optBoolean("monthly"),
            field("pay"),
            field("worked"),
            field("planned"),
            field("bonus"),
            field("deduction"),
        )
    }
}
