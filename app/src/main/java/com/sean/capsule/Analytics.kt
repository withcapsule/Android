package com.sean.capsule

import dev.appoutlet.umami.Umami

val analytics = Umami(website = "dc95a983-0bfb-44de-9a15-9c002701641d") {
    baseUrl("https://analytics2.byseansingh.com")
    hostname("com.sean.capsule")
}
