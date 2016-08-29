package support

import catalogue.core.Launcher
import catalogue.core.storage.Storage

import static support.HttpClient.get

class World {
    static reset() {
        Storage.clear()
    }

    static def startApi() {
        Launcher.start()
    }

    static def stopApi() {
        Launcher.stop()
    }

    static URL itemApiRoot() {
        new URL(get(World.apiRoot()).links.items)
    }

    static URL apiRoot() {
        def directAddress = new URL('http://localhost:9402/catalogue')
        def useOkapi = (System.getProperty("okapi.use") ?: "").toBoolean()

        useOkapi ? new URL(System.getProperty("okapi.address") + '/catalogue') : directAddress
    }
}
