package com.example.gestorempresa.domain.phone

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

data class PhoneInput(val region: String = "ES", val number: String = "")

data class PhoneCountry(val region: String, val name: String, val callingCode: Int) {
    val flag: String get() = region.map { Character.toChars(0x1F1E6 + it.code - 'A'.code).concatToString() }.joinToString("")
    val label: String get() = "$flag +$callingCode · $name"
}

/** Metadatos de numeración por país. Room sigue guardando un String, ahora en E.164. */
object InternationalPhones {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val spanish = Locale.forLanguageTag("es")
    private val allowed = Regex("\\+?[0-9() .\\-]+")
    val countries: List<PhoneCountry> by lazy {
        val collator = Collator.getInstance(spanish)
        util.supportedRegions.map { region ->
            PhoneCountry(region, Locale.Builder().setRegion(region).build().getDisplayCountry(spanish), util.getCountryCodeForRegion(region))
        }.sortedWith { a, b ->
            when { a.region == b.region -> 0; a.region == "ES" -> -1; b.region == "ES" -> 1; else -> collator.compare(a.name, b.name) }
        }
    }

    fun country(region: String): PhoneCountry = countries.firstOrNull { it.region == region } ?: countries.first { it.region == "ES" }

    fun search(query: String): List<PhoneCountry> {
        val normalized = searchText(query.trim())
        return countries.filter { normalized in searchText("${it.name} ${it.region} +${it.callingCode}") }
    }

    private fun searchText(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    /** No recorta ni borra caracteres: una entrada inválida debe poder corregirse. */
    fun toE164(number: String, region: String): String? {
        if (region !in util.supportedRegions) return null
        val parsed = validNumber(number, region) ?: return null
        return if (parsed.countryCode == util.getCountryCodeForRegion(region))
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164) else null
    }

    /** Contactos que ya incluyen prefijo, también para números internacionales cortos. */
    fun internationalE164(number: String): String? {
        if (!hasInternationalPrefix(number.trim())) return null
        return validNumber(number, "ZZ")?.let { util.format(it, PhoneNumberUtil.PhoneNumberFormat.E164) }
    }

    private fun validNumber(number: String, region: String): PhoneNumber? {
        val text = number.trim()
        if (text.isEmpty() || text.length > 64 || !allowed.matches(text)) return null
        return try {
            val parsed = util.parse(internationalPrefix(text), region)
            parsed.takeIf { !it.hasExtension() && util.isValidNumber(it) &&
                util.format(it, PhoneNumberUtil.PhoneNumberFormat.E164).matches(Regex("\\+[1-9][0-9]{1,14}")) }
        } catch (_: NumberParseException) { null }
    }

    /** Conserva los números locales antiguos; un prefijo explícito permite restaurar su país. */
    fun restore(stored: String, defaultRegion: String = "ES"): PhoneInput {
        val text = stored.trim()
        if (!hasInternationalPrefix(text) || text.length > 64 || !allowed.matches(text)) return PhoneInput(defaultRegion, text)
        return try {
            val parsed = util.parse(internationalPrefix(text), "ZZ")
            val region = util.getRegionCodeForNumber(parsed)?.takeIf { it in util.supportedRegions }
                ?: util.getRegionCodeForCountryCode(parsed.countryCode).takeIf { it in util.supportedRegions }
                ?: return PhoneInput(defaultRegion, text)
            PhoneInput(region, util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL))
        } catch (_: NumberParseException) { PhoneInput(defaultRegion, text) }
    }

    /** Pegar un número internacional completo selecciona automáticamente su país. */
    fun edit(text: String, region: String): PhoneInput {
        if (hasInternationalPrefix(text.trim())) {
            val restored = restore(text, region)
            if (toE164(restored.number, restored.region) != null) return restored
        }
        return PhoneInput(region, text)
    }

    private fun hasInternationalPrefix(text: String) = text.startsWith("+") || text.startsWith("00")
    private fun internationalPrefix(text: String) = if (text.startsWith("00")) "+${text.drop(2)}" else text
}
