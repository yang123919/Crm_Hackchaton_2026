package com.example.hackchaton_2026_crm.data

/** Dropdown values from the CS Group CRM. Replace with GCR's export when available. */
object ReferenceLists {
    val products = listOf(
        "5008 Allure Plus SUV",
        "3008 MidLife",
        "308 GTi",
        "2008 PureTech 130",
        "Hyundai Ioniq Hev Plus",
        "Hyundai AD Elantra 2.0",
        "Santa Fe 2.4 Premium",
        "Hyundai Starex",
        "Omoda 5 Comfort",
        "Tiggo 8 Pro",
        "GWM Cannon",
        "Proton Iriz",
        "Proton Saga",
        "Proton Exora",
        "Proton X50",
        "C3 Aircross SUV",
    )

    val outlets = listOf(
        "CS Insta",
        "Butterworth Sunway Carnival",
        "SP Village Mall",
        "Alor Setar Aeon Big",
        "Alor Setar Outlet",
        "Alor Setar Aman Central",
        "Alor Setar Tesco Mergong",
        "B.M AEON Alma",
    )

    val salesStages = listOf(
        "Prospecting",
        "Follow Up",
        "Test Drive",
        "Quotation",
        "Follow up 2",
        "Booking",
        "Pending Booking Refund",
        "Booking Refund",
        "Bank Loan Submission",
        "LOU",
        "Allocation Complete",
        "Full Downpayment",
        "Issue Downpayment Receipt",
        "Registration",
        "Delivery",
        "Finance Disbursements",
        "Completed",
        "Closed Won",
    )

    const val DEFAULT_STAGE = "Follow Up"

    /** Stages after which no follow-up reminder is needed. */
    val closedStages = setOf("Completed", "Closed Won", "Booking Refund")
}
