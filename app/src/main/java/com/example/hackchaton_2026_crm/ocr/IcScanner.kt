package com.example.hackchaton_2026_crm.ocr

import android.content.Context
import android.net.Uri

/** Reads a MyKad photo into name, IC number, address and gender. */
object IcScanner {
    suspend fun scan(context: Context, uri: Uri): IcInfo = MyKadParser.parse(TextReader.rows(context, uri))
}
