package support

import catalogue.core.Launcher

class World {
    static reset() {

    }

    static def startApi() {
        Launcher.start()
    }

    static def stopApi() {
        Launcher.stop()
    }

    static URL apiRoot() {
        def directAddress = new URL('http://localhost:9402/catalogue')
        def useOkapi = (System.getProperty("okapi.use") ?: "").toBoolean()

        useOkapi ? new URL(System.getProperty("okapi.address") + '/catalogue') : directAddress
    }
}
