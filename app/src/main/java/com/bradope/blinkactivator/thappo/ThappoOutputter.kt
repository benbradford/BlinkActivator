package com.bradope.blinkactivator.thappo

interface ThappoOutputter {
    fun log(tag: String, msg: String, priority: ThappoPriority)
}