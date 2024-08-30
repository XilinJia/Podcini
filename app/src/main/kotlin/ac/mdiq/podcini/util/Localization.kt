package ac.mdiq.podcini.util

import ac.mdiq.vista.extractor.exceptions.ParsingException
import ac.mdiq.vista.extractor.localization.ContentCountry
import ac.mdiq.vista.extractor.utils.LocaleCompat.forLanguageTag
import android.content.Context
import androidx.preference.PreferenceManager
import java.io.Serializable
import java.util.*

// derived from VoiVista
class Localization @JvmOverloads constructor(
        @JvmField val languageCode: String,
        private val countryCode: String? = null) : Serializable {

    /**
     * Return a formatted string in the form of: `language-Country`, or
     * just `language` if country is `null`.
     * @return A correctly formatted localizationCode for this localization.
     */
    private val localizationCode: String
        get() = languageCode + (if (countryCode == null) "" else "-$countryCode")

    fun getCountryCode(): String {
        return countryCode ?: ""
    }

    override fun toString(): String {
        return "Localization[$localizationCode]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Localization) return false
        return languageCode == other.languageCode && countryCode == other.getCountryCode()
    }

    override fun hashCode(): Int {
        var result = languageCode.hashCode()
        result = 31 * result + Objects.hashCode(countryCode)
        return result
    }

    companion object {
        @JvmField
        val DEFAULT: Localization = Localization("en", "GB")

        /**
         * @param localizationCodeList a list of localization code, formatted like [                             ][.getLocalizationCode]
         * @throws IllegalArgumentException If any of the localizationCodeList is formatted incorrectly
         * @return list of Localization objects
         */
        fun listFrom(vararg localizationCodeList: String): List<Localization> {
            val toReturn: MutableList<Localization> = ArrayList()
            for (localizationCode in localizationCodeList) {
                toReturn.add(fromLocalizationCode(localizationCode) ?: throw IllegalArgumentException("Not a localization code: $localizationCode"))
            }
            return Collections.unmodifiableList(toReturn)
        }

        /**
         * @param localizationCode a localization code, formatted like [.getLocalizationCode]
         * @return A Localization, if the code was valid.
         */
        private fun fromLocalizationCode(localizationCode: String): Localization? {
            return forLanguageTag(localizationCode)?.let { locale: Locale -> fromLocale(locale) }
        }

        private fun fromLocale(locale: Locale): Localization {
            return Localization(locale.language, locale.country)
        }

        /**
         * Converts a three letter language code (ISO 639-2/T) to a Locale
         * because limits of Java Locale class.
         *
         * @param code a three letter language code
         * @return the Locale corresponding
         */

        @Throws(ParsingException::class)
        fun getLocaleFromThreeLetterCode(code: String): Locale? {
            val languages = Locale.getISOLanguages()
            val localeMap: MutableMap<String, Locale> = HashMap(languages.size)
            for (language in languages) {
                val locale = Locale(language)
                localeMap[locale.isO3Language] = locale
            }
            if (localeMap.containsKey(code)) return localeMap[code]
            else throw ParsingException("Could not get Locale from this three letter language code$code")
        }

        fun getPreferredLocalization(context: Context): ac.mdiq.vista.extractor.localization.Localization {
            return ac.mdiq.vista.extractor.localization.Localization.fromLocale(getPreferredLocale(context))
        }

        @JvmStatic
        fun getPreferredLocale(context: Context): Locale {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val defaultKey = "system"
            val languageCode = sp.getString("content_country", defaultKey)
            return if (languageCode == defaultKey) Locale.getDefault() else  Locale.forLanguageTag(languageCode!!)
        }

        @JvmStatic
        fun getPreferredContentCountry(context: Context): ContentCountry {
            val contentCountry = PreferenceManager.getDefaultSharedPreferences(context).getString("content_country", "system")
            if (contentCountry == "system") return ContentCountry(Locale.getDefault().country)
            return ContentCountry(contentCountry!!)
        }
    }
}
