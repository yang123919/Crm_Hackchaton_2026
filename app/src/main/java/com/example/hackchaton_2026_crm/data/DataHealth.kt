package com.example.hackchaton_2026_crm.data

import com.example.hackchaton_2026_crm.ai.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class IssueKind(val label: String) {
    DUPLICATE_CUSTOMER("Duplicate customer"),
    DUPLICATE_HISTORY("Duplicate history"),
    INVALID_PHONE("Invalid phone number"),
    INCONSISTENT("Details don't add up"),
    STALE_FOLLOW_UP("Stale follow-up"),
}

/** Declared worst-first so sorting by severity puts the urgent problems on top. */
enum class Severity { HIGH, MEDIUM, LOW }

/** A change the app can make by itself; problems without one need the salesperson to open the customer. */
sealed interface HealthFix {
    val label: String

    data class MergeCustomers(val keeperId: Long, val dropIds: List<Long>) : HealthFix {
        override val label: String get() = "Merge"
    }

    data class RemoveEntries(val entryIds: List<Long>) : HealthFix {
        override val label: String get() = "Remove duplicates"
    }
}

data class HealthIssue(
    /** Stable between scans, so AI advice already fetched for an issue is kept. */
    val id: String,
    val kind: IssueKind,
    val severity: Severity,
    /** The customer to open to look at it. */
    val leadId: Long,
    val title: String,
    /** What was found, as plain facts. */
    val detail: String,
    /** The built-in recommended action; shown until (or instead of) the AI's explanation. */
    val todo: String,
    val fix: HealthFix? = null,
)

/**
 * Rule-based scan for customer data that is wrong or suspicious. The rules find the problems
 * (fast, offline, always the same answer); the AI then explains them in plain words.
 */
object DataHealth {
    private const val STALE_DAYS = 14L
    private const val MIN_BUDGET_RM = 10_000L
    private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun scan(leads: List<Lead>, entries: List<FollowUpEntry>, today: LocalDate): List<HealthIssue> =
        (duplicateCustomers(leads, entries) + duplicateHistory(leads, entries) + leads.flatMap { leadProblems(it, today) })
            .sortedWith(compareBy<HealthIssue>({ it.severity }, { it.kind }, { it.title }))

    /** The customer that survives a merge: gaps in it are filled from the others. */
    fun merge(keeper: Lead, others: List<Lead>): Lead = others.fold(keeper) { k, o ->
        k.copy(
            email = k.email.filled() ?: o.email.filled(),
            icNumber = k.icNumber.filled() ?: o.icNumber.filled(),
            address = k.address.filled() ?: o.address.filled(),
            gender = k.gender.filled() ?: o.gender.filled(),
            budgetRm = k.budgetRm ?: o.budgetRm,
            expectedCloseDate = k.expectedCloseDate ?: o.expectedCloseDate,
            nextFollowUpAt = k.nextFollowUpAt ?: o.nextFollowUpAt,
            nextAction = k.nextAction.filled() ?: o.nextAction.filled(),
            tradeInUsedCar = k.tradeInUsedCar || o.tradeInUsedCar,
            tradeInBrand = k.tradeInBrand.filled() ?: o.tradeInBrand.filled(),
            tradeInModel = k.tradeInModel.filled() ?: o.tradeInModel.filled(),
            tradeInYear = k.tradeInYear ?: o.tradeInYear,
            createdAt = minOf(k.createdAt, o.createdAt),
        )
    }

    private fun String?.filled(): String? = this?.takeIf { it.isNotBlank() }

    private fun icKey(lead: Lead): String? = lead.icNumber?.filter(Char::isDigit)?.takeIf { it.length == 12 }

    private fun phoneKey(lead: Lead): String? = lead.mobile.filter(Char::isDigit).takeIf { it.isNotEmpty() }

    private fun completeness(lead: Lead): Int =
        listOf(lead.email, lead.icNumber, lead.address, lead.gender, lead.nextAction)
            .count { !it.isNullOrBlank() } +
            listOf(lead.budgetRm, lead.expectedCloseDate, lead.nextFollowUpAt).count { it != null } +
            (if (lead.tradeInUsedCar) 1 else 0)

    // ---- Duplicate customers ----------------------------------------------------------

    /** Customers sharing an IC number or a phone number, chained together (A=B and B=C makes one group). */
    private fun duplicateCustomers(leads: List<Lead>, entries: List<FollowUpEntry>): List<HealthIssue> {
        val parent = IntArray(leads.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (key in listOf(::icKey, ::phoneKey)) {
            val firstWith = HashMap<String, Int>()
            leads.forEachIndexed { i, lead ->
                val k = key(lead) ?: return@forEachIndexed
                val first = firstWith.getOrPut(k) { i }
                if (first != i) parent[find(i)] = find(first)
            }
        }
        val entryCount = entries.groupingBy { it.leadId }.eachCount()

        return leads.indices.groupBy(::find).values
            .filter { it.size > 1 }
            .map { group ->
                val members = group.map { leads[it] }
                val keeper = members.maxWith(
                    compareBy<Lead> { completeness(it) + (entryCount[it.id] ?: 0) }.thenByDescending { it.id },
                )
                val drop = members.filter { it.id != keeper.id }.map { it.id }.sorted()
                val sharedIc = members.mapNotNull(::icKey).groupingBy { it }.eachCount().any { it.value > 1 }
                val sharedPhone = members.mapNotNull(::phoneKey).groupingBy { it }.eachCount().any { it.value > 1 }
                val why = when {
                    sharedIc && sharedPhone -> "the same IC number and phone number"
                    sharedIc -> "the same IC number"
                    else -> "the same phone number"
                }
                HealthIssue(
                    id = "dup:${keeper.id}:${drop.joinToString(",")}",
                    kind = IssueKind.DUPLICATE_CUSTOMER,
                    severity = Severity.HIGH,
                    leadId = keeper.id,
                    title = "${keeper.fullName} is saved ${members.size} times",
                    detail = members.joinToString(" / ") { it.fullName } + " have $why." +
                        if (sharedIc) "" else " Check they are the same person before merging.",
                    todo = "Merge them into one customer. Their history is combined and nothing is lost.",
                    fix = HealthFix.MergeCustomers(keeper.id, drop),
                )
            }
    }

    // ---- Duplicate history ------------------------------------------------------------

    private fun duplicateHistory(leads: List<Lead>, entries: List<FollowUpEntry>): List<HealthIssue> {
        val names = leads.associate { it.id to it.fullName }
        return entries
            .groupBy { Triple(it.leadId, it.date, historyKey(it)) }
            .filterValues { it.size > 1 }
            .map { (key, group) ->
                val (leadId, date, kind) = key
                val keep = group.maxWith(compareBy<FollowUpEntry> { it.createdAt }.thenBy { it.id })
                val remove = group.filter { it.id != keep.id }.map { it.id }.sorted()
                val what = if (kind == WHATSAPP_KEY) "\"WhatsApp sent\" entries" else "identical notes"
                HealthIssue(
                    id = "hist:$leadId:$date:${remove.first()}",
                    kind = IssueKind.DUPLICATE_HISTORY,
                    severity = Severity.MEDIUM,
                    leadId = leadId,
                    title = "${group.size} $what on ${dayFormat.format(date)}",
                    detail = "${names[leadId] ?: "This customer"}'s history has ${group.size} $what for the same day.",
                    todo = "Keep the latest and remove the other ${remove.size}.",
                    fix = HealthFix.RemoveEntries(remove),
                )
            }
    }

    private const val WHATSAPP_KEY = "whatsapp"

    private fun historyKey(e: FollowUpEntry): String =
        if (e.summary.startsWith(FollowUpEntry.WHATSAPP_PREFIX)) WHATSAPP_KEY else e.summary.trim().lowercase()

    // ---- Per-customer problems --------------------------------------------------------

    private fun leadProblems(lead: Lead, today: LocalDate): List<HealthIssue> = buildList {
        if (!Normalizer.isValidMobile(lead.mobile)) {
            add(
                HealthIssue(
                    id = "phone:${lead.id}",
                    kind = IssueKind.INVALID_PHONE,
                    severity = Severity.HIGH,
                    leadId = lead.id,
                    title = "${lead.fullName}: phone number looks wrong",
                    detail = "\"${lead.mobile}\" is not a valid Malaysian mobile number, so calls and WhatsApp will fail.",
                    todo = "Open the customer and correct the number.",
                ),
            )
        }

        val problems = buildList {
            if (lead.tradeInUsedCar && lead.tradeInBrand.isNullOrBlank() && lead.tradeInModel.isNullOrBlank()) {
                add("trade-in is ticked but no car is given")
            }
            if (!lead.tradeInUsedCar &&
                (!lead.tradeInBrand.isNullOrBlank() || !lead.tradeInModel.isNullOrBlank() || lead.tradeInYear != null)
            ) {
                add("a trade-in car is given but trade-in is off")
            }
            lead.tradeInYear?.let { if (Normalizer.year(it, today) == null) add("trade-in year $it is not a real year") }
            if (!lead.email.isNullOrBlank() && !Normalizer.isValidEmail(lead.email)) add("email \"${lead.email}\" is not valid")
            lead.budgetRm?.let {
                if (it < MIN_BUDGET_RM) add("budget RM $it is very low (typed in thousands?)")
            }
            lead.expectedCloseDate?.let {
                val added = lead.createdAt.atZone(SeedData.ZONE).toLocalDate()
                if (it < added) add("close date ${dayFormat.format(it)} is before the customer was added")
            }
        }
        if (problems.isNotEmpty()) {
            add(
                HealthIssue(
                    id = "check:${lead.id}",
                    kind = IssueKind.INCONSISTENT,
                    severity = Severity.MEDIUM,
                    leadId = lead.id,
                    title = "${lead.fullName}: details don't add up",
                    detail = problems.joinToString("; ").replaceFirstChar(Char::uppercase) + ".",
                    todo = "Open the customer and correct the details.",
                ),
            )
        }

        val due = lead.nextFollowUpAt
        if (due != null && lead.salesStage !in ReferenceLists.closedStages) {
            val days = ChronoUnit.DAYS.between(due, today)
            if (days >= STALE_DAYS) {
                add(
                    HealthIssue(
                        id = "stale:${lead.id}",
                        kind = IssueKind.STALE_FOLLOW_UP,
                        severity = Severity.LOW,
                        leadId = lead.id,
                        title = "${lead.fullName}: follow-up $days days overdue",
                        detail = "The next step${lead.nextAction.filled()?.let { " (\"$it\")" }.orEmpty()} was due ${dayFormat.format(due)} and has not been updated.",
                        todo = "Follow up now, set a new date, or mark the customer closed.",
                    ),
                )
            }
        }
    }
}
