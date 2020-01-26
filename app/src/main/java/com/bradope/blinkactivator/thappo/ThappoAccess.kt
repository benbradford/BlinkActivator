package com.bradope.blinkactivator.thappo


// THis hAPpened-o
// A logging service

enum class ThappoPriority {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}

private var defaultTag = "Thappo_Default"

// :TODO: pass in builder or something similar
fun thappoInit() {

}

fun setDefaultTag(tag: String) { defaultTag = tag }

fun thappo(msg: String) {
    thappo(defaultTag, msg, ThappoPriority.INFO)
}

fun thappo(msg: String, prio: ThappoPriority) {
    thappo(defaultTag, msg, prio)
}

fun thappo(tag: String, msg: String, priority: ThappoPriority = ThappoPriority.INFO) {
    // :TODO: send it down chain
}