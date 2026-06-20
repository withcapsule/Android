package com.sean.capsule

import dev.appoutlet.umami.Umami

val analytics = Umami(website = "b85c84d7-1ff9-4b0b-ae29-e7694d4cd48f") {
    baseUrl("https://au.withcapsule.dev/")
    hostname("com.sean.capsule")
}
