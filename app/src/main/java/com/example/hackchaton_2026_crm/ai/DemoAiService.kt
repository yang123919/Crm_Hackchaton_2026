package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.HealthIssue
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.MessageLanguage
import com.example.hackchaton_2026_crm.data.SeedData
import com.example.hackchaton_2026_crm.data.Severity
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Offline stand-in for the LLM ("Demo mode"). Rule-based, so it handles the sample lines in
 * docs/PLAN.md and similar sentences with no network. Returns the same shapes as the real AI.
 */
class DemoAiService(private val delayMs: Long = 1_200) : AiService {

    override suspend fun extractLead(transcript: String, today: LocalDate): LeadExtraction {
        delay(delayMs)
        return FormExtraction.enrich(extractFreeText(transcript, today), transcript, today)
    }

    private fun extractFreeText(transcript: String, today: LocalDate): LeadExtraction {
        val text = Normalizer.spokenDigitsToNumbers(transcript)
        val lower = text.lowercase()

        val trade = tradeInRegex.find(text)
        val withoutTrade = trade?.let { text.removeRange(it.range) } ?: text
        val tradeModel = trade?.groupValues?.get(1)?.replaceFirstChar(Char::uppercase)
        val tradeYear = trade?.groupValues?.get(2)?.toIntOrNull()

        val (first, last) = findName(text)
        val product = findAlias(withoutTrade, productAliases)
        val budget = budgetRegex.find(text)?.groupValues?.get(1)?.let(Normalizer::budget)
        val phone = Normalizer.findPhone(transcript)
        val dates = Normalizer.findDates(text, today)
        val testDriveDate = dates.firstOrNull { (range, _) ->
            lower.substring(maxOf(0, range.first - 15), range.first).contains("test drive")
        }?.second
        val nextDate = dates.lastOrNull { (range, _) ->
            followUpVerbs.containsMatchIn(lower.substring(maxOf(0, range.first - 25), range.first))
        }?.second ?: dates.lastOrNull()?.second

        val wantsTestDrive = "test drive" in lower
        val wantsQuote = quoteRegex.containsMatchIn(lower)
        val nextAction = when {
            wantsQuote && "whatsapp" in lower -> "Send quotation on WhatsApp"
            wantsQuote -> "Send quotation"
            wantsTestDrive && "call" in lower -> "Call back about test drive"
            wantsTestDrive -> "Arrange test drive"
            "call" in lower -> "Call back"
            "whatsapp" in lower -> "WhatsApp follow-up"
            else -> "Follow up"
        }

        val summary = listOfNotNull(
            product?.let { "Interested in $it" } ?: "New enquiry",
            when {
                budget != null -> "budget ~RM${budget / 1_000}k"
                "no budget" in lower -> "no budget yet"
                else -> null
            },
        ).joinToString(", ") + "." + listOfNotNull(
            tradeModel?.let { " Trade-in ${listOfNotNull(brandFor(it), it, tradeYear).joinToString(" ")}." },
            testDriveDate?.let { " Wants a test drive on ${shortDate.format(it)}." },
        ).joinToString("")

        return LeadExtraction(
            firstName = first,
            lastName = last,
            mobile = phone,
            email = emailRegex.find(text)?.value,
            product = product,
            outlet = findAlias(text, outletAliases),
            budgetRm = budget?.toDouble(),
            tradeIn = TradeIn(
                usedCar = trade != null,
                brand = tradeModel?.let(::brandFor),
                model = tradeModel,
                year = tradeYear,
            ),
            followUp = FollowUpPart(summary, nextAction, nextDate?.toString()),
            suggestedSalesStage = stageFor(lower, fallback = "Follow Up"),
            lowConfidenceFields = buildList {
                if (phone != null && Normalizer.phone(phone) == null) add("mobile")
                if (first != null && last == null) add("last_name")
            },
        )
    }

    override suspend fun extractFollowUp(transcript: String, lead: Lead, today: LocalDate): FollowUpExtraction {
        delay(delayMs)
        val lower = transcript.lowercase()
        val stage = stageFor(lower, fallback = lead.salesStage)
        val summary = transcript.trim()
            .replace(leadInRegex, "")
            .replaceFirstChar(Char::uppercase)
            .let { if (it.endsWith(".")) it else "$it." }
        val nextAction = when (stage) {
            "Booking" -> "Collect booking fee"
            "Test Drive" -> "Arrange test drive"
            "Quotation" -> "Send quotation"
            "Bank Loan Submission" -> "Submit loan documents"
            else -> "Follow up"
        }
        val nextDate = Normalizer.findDates(transcript, today).lastOrNull()?.second ?: today.plusDays(3)
        return FollowUpExtraction(FollowUpPart(summary, nextAction, nextDate.toString()), stage)
    }

    override suspend fun draftWhatsApp(
        lead: Lead,
        lastEntry: FollowUpEntry?,
        language: MessageLanguage,
        salesperson: String,
    ): String {
        delay(delayMs / 2)
        // An overdue date would read oddly in a message ("test drive on Sun 18 Oct?"), so skip it.
        val date = lead.nextFollowUpAt?.takeIf { !it.isBefore(LocalDate.now(SeedData.ZONE)) }?.let(shortDate::format)
        // Rotate wording so "Regenerate" visibly gives a different draft each time.
        val v = draftCount++
        val name = lead.fullName
        val car = lead.product
        return if (language == MessageLanguage.BM) {
            val line = when (lead.salesStage) {
                "Test Drive" -> "Bila masa yang sesuai untuk pandu uji${date?.let { ", mungkin $it" } ?: ""}?"
                "Quotation" -> "Saya akan hantar sebut harga sebentar lagi."
                "Booking" -> "Sekadar peringatan tentang tempahan anda${date?.let { " pada $it" } ?: ""}."
                else -> "Bila masa yang sesuai untuk kita berbincang?"
            }
            val opening = listOf(
                "Hi $name, saya $salesperson dari CS Group. Terima kasih atas minat anda terhadap $car!",
                "Salam $name, $salesperson dari CS Group di sini. Gembira dapat membantu anda dengan $car.",
                "Hai $name! Ini $salesperson dari CS Group, berkenaan $car yang anda minati.",
            )
            val closing = listOf(
                "Jika ada sebarang soalan, beritahu saya ya.",
                "Saya sedia membantu bila-bila masa.",
                "Terima kasih, semoga hari anda baik!",
            )
            "${opening[v % opening.size]} $line ${closing[v % closing.size]} - $salesperson"
        } else {
            val line = when (lead.salesStage) {
                "Test Drive" -> "Would you like to arrange a test drive${date?.let { " on $it" } ?: ""}?"
                "Quotation" -> "I'll send you the quotation shortly."
                "Booking" -> "Just a reminder about your booking${date?.let { " on $it" } ?: ""}."
                else -> "When would be a good time to talk?"
            }
            val opening = listOf(
                "Hi $name, this is $salesperson from CS Group. Thank you for your interest in the $car!",
                "Hello $name, $salesperson here from CS Group. Great to hear you're keen on the $car.",
                "Hi $name! $salesperson from CS Group following up on the $car.",
            )
            val closing = listOf(
                "Let me know if you have any questions.",
                "Happy to help with anything you need.",
                "Thanks, and have a great day!",
            )
            "${opening[v % opening.size]} $line ${closing[v % closing.size]} - $salesperson"
        }
    }

    private var draftCount = 0

    /** Offline stand-in: the scan's own recommended action per problem, plus a canned verdict. */
    override suspend fun explainDataHealth(issues: List<HealthIssue>): HealthAdvice {
        delay(delayMs)
        val worst = issues.minOfOrNull { it.severity }
        val summary = when (worst) {
            null -> "Your customer data looks clean."
            Severity.HIGH -> "Some records are likely wrong and will cause missed calls or double work. Fix those first."
            else -> "No serious errors, but a few records need tidying so reports stay accurate."
        }
        return HealthAdvice(summary, issues.map { it.todo })
    }

    // ---- Rules ------------------------------------------------------------------------

    private fun stageFor(lower: String, fallback: String) = when {
        "booking" in lower || "tempah" in lower -> "Booking"
        "loan" in lower || "pinjaman" in lower -> "Bank Loan Submission"
        quoteRegex.containsMatchIn(lower) -> "Quotation"
        "test drive" in lower || "pandu uji" in lower -> "Test Drive"
        else -> fallback
    }

    private fun findName(text: String): Pair<String?, String?> {
        val m = nameRegex.find(text) ?: return null to null
        val words = m.groupValues[1].split(Regex("""\s+"""))
            .takeWhile { it.lowercase() !in nameStopWords }
            .map { w -> w.replaceFirstChar(Char::uppercase) }
        return when (words.size) {
            0 -> null to null
            1 -> words[0] to null
            else -> words.dropLast(1).joinToString(" ") to words.last()
        }
    }

    private fun findAlias(text: String, aliases: List<Pair<String, String>>): String? {
        val lower = text.lowercase()
        return aliases
            .mapNotNull { (alias, value) ->
                Regex("""\b${Regex.escape(alias)}\b""").find(lower)?.let { it.range.first to value }
            }
            .minByOrNull { it.first }?.second
    }

    private fun brandFor(model: String) = tradeInBrands[model.lowercase()]

    private companion object {
        val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

        val nameRegex = Regex(
            """(?i:\b(?:mr|mrs|ms|miss|mdm|madam|puan|pn|encik|cik|dato'?|datin|dr)\.?\s+)([A-Za-z'][\w']*(?:\s+[A-Za-z'][\w']*){0,3})""",
        )
        val nameStopWords = setOf(
            "from", "wants", "want", "nak", "interested", "is", "at", "with", "and", "the", "he", "she",
            "looking", "called", "says", "said", "who", "ok", "okay", "will", "also", "dari", "mahu",
        )
        val tradeInRegex = Regex("""(?i)\btrade[- ]?in\s+(?:(?:a|an|his|her|my|the|with|old)\s+)?([A-Za-z]+)(?:\s+(\d{4}))?""")
        val budgetRegex = Regex("""(?i)budget\D{0,20}?((?:rm\s*)?\d[\d,.]*\s*(?:k|thousand|ribu|mil|million|juta)?)""")
        val emailRegex = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
        val quoteRegex = Regex("""\b(quotation|quote|sebut harga)\b""")
        val followUpVerbs = Regex("""\b(call|whatsapp|follow[- ]?up|contact|remind|send|hubungi)\b""")
        val leadInRegex = Regex(
            """^(?i:(?:called|met|spoke to|talked to|whatsapped|messaged)\s+(?:(?:mr|mrs|ms|puan|encik|cik|madam)\.?\s+)?[\w']+(?:\s+[\w']+)?,\s*)""",
        )

        /** Spoken alias → exact product name; first mention in the sentence wins. */
        val productAliases = listOf(
            "5008" to "5008 Allure Plus SUV",
            "3008" to "3008 MidLife",
            "308" to "308 GTi",
            "puretech" to "2008 PureTech 130",
            "peugeot 2008" to "2008 PureTech 130",
            "ioniq" to "Hyundai Ioniq Hev Plus",
            "elantra" to "Hyundai AD Elantra 2.0",
            "santa fe" to "Santa Fe 2.4 Premium",
            "starex" to "Hyundai Starex",
            "omoda" to "Omoda 5 Comfort",
            "tiggo" to "Tiggo 8 Pro",
            "cannon" to "GWM Cannon",
            "iriz" to "Proton Iriz",
            "saga" to "Proton Saga",
            "exora" to "Proton Exora",
            "x50" to "Proton X50",
            "x 50" to "Proton X50",
            "aircross" to "C3 Aircross SUV",
        )

        val outletAliases = listOf(
            "butterworth" to "Butterworth Sunway Carnival",
            "sunway carnival" to "Butterworth Sunway Carnival",
            "village mall" to "SP Village Mall",
            "sungai petani" to "SP Village Mall",
            "aeon big" to "Alor Setar Aeon Big",
            "aman central" to "Alor Setar Aman Central",
            "mergong" to "Alor Setar Tesco Mergong",
            "alma" to "B.M AEON Alma",
            "insta" to "CS Insta",
            "alor setar" to "Alor Setar Outlet",
        )

        val tradeInBrands = mapOf(
            "myvi" to "Perodua", "axia" to "Perodua", "bezza" to "Perodua", "alza" to "Perodua",
            "kancil" to "Perodua", "viva" to "Perodua", "aruz" to "Perodua", "ativa" to "Perodua",
            "saga" to "Proton", "persona" to "Proton", "wira" to "Proton", "waja" to "Proton", "preve" to "Proton",
            "city" to "Honda", "civic" to "Honda", "jazz" to "Honda", "hrv" to "Honda", "crv" to "Honda",
            "vios" to "Toyota", "camry" to "Toyota", "yaris" to "Toyota", "corolla" to "Toyota",
            "almera" to "Nissan", "x70" to "Proton", "x50" to "Proton",
        )
    }
}
