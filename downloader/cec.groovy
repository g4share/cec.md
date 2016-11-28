@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.2')

import groovyx.net.http.*

def f = new File('cec.env')
if(!f.exists() || f.isDirectory()) {
    echo "Configuration not found: ${f.name}" , '[0;31m'
    System.exit(1)
}

def config = new ConfigSlurper().parse(f.text)
def cecBuilder = new HTTPBuilder(config.site.url)
if (config.proxy) {
    cecBuilder.setProxy(config.proxy.url, config.proxy.port, 'http')
}

def xmlHttp = new XMLHttpUtils(httpBuilder: cecBuilder)
def binaryHttp = new BinaryHttpUtils(httpBuilder: cecBuilder)

def pagePaths = [
    new RequestInfo(xmlPage: 'alegeri.xml'),
    new RequestInfo(xmlPage: 'circumscriptii.xml'),
    new RequestInfo(xmlPage: 'localitati.xml', addIdToPngTemplate: false),
    new RequestInfo(xmlPage: 'sectii.xml', addIdToPngTemplate: false),
    new RequestInfo()]

load(new CecInfo(xmlHttp: xmlHttp, 
                binaryHttp: binaryHttp, 
                pagePaths: pagePaths,
                rootFolder: createDataStorage(), 
                xmlRootPath: config.site.root),
    0, "", " ")


def load(cecInfo, currentIdx, xmlPagePart, alignText) {
    def storyFolder = new File(cecInfo.rootFolder, "${xmlPagePart}")
    storyFolder.mkdirs()

    def requestInfo = cecInfo.pagePaths[currentIdx]
    if (requestInfo.xmlPage) {
        println "${alignText}${xmlPagePart}/${requestInfo.xmlPage}"
        def xmlUrl = "${cecInfo.xmlRootPath}${xmlPagePart}/${requestInfo.xmlPage}"

        cecInfo.xmlHttp.exec(xmlUrl, new File(storyFolder, requestInfo.xmlPage)) { xml ->
            xml.values.each { value -> 
                if (currentIdx < cecInfo.pagePaths.size()) {
                    load(cecInfo, currentIdx + 1, "${xmlPagePart}/${value.id}", alignText * 2)
                }

                downloadPng(cecInfo.binaryHttp, 
                    requestInfo, 
                    cecInfo.xmlRootPath, 
                    xmlPagePart, 
                    value.id, 
                    new File(storyFolder, "${value.id}/"), 
                    alignText)
            }
        }
    }
}

println ''

def createDataStorage() {
    def dt = new Date()
    def rootFolder = new File("data/${dt.format('yyyyMMdd')}/${dt.format('HH.mm')}")
    if (rootFolder.exists()) {
        rootFolder.deleteDir();
    }
    rootFolder.mkdirs()
    rootFolder
}

def echo(data, color = '[33m') {
    def esc = (char)27
    print "${esc}${color}${data}${esc}[0m"
}

def downloadPng(binaryHttp, requestInfo, xmlRootPath, xmlPagePart, id, storyFolder, alignText) {
    def idPngUrl = requestInfo.addIdToPngTemplate ? "/${id}" : '';
    def pngUrl = "${xmlRootPath}${xmlPagePart}${idPngUrl}/ro${id}.png"

    echo "${alignText}${xmlPagePart}${idPngUrl}/ro${id}.png\n"
            
    binaryHttp.exec("${pngUrl}", new File(storyFolder, "ro${id}.png"))
}

class XMLHttpUtils {
    def HTTPBuilder httpBuilder

    def exec(url, storeFile, Closure c = null) {
        httpBuilder.request(Method.GET, ContentType.TEXT) {
            uri.path = url
            response.success = { resp, reader ->
                assert resp.statusLine.statusCode == 200
                storeFile.withWriter('UTF-8') { writer ->
                    writer.write(reader.text.substring(1))
                }
                if (c) c(new XmlSlurper().parse(storeFile))
            }

            response.failure = response.success
        }
    }
}

class BinaryHttpUtils {
    def HTTPBuilder httpBuilder

    def exec(url, storeFile) {
        httpBuilder.request(Method.GET, ContentType.BINARY) {
            uri.path = url
            response.success = { resp, reader ->
                if (resp.statusLine.statusCode == 200 ) {
                    def fos = new FileOutputStream(storeFile)
                    fos << reader
                    fos.close()
                }
            }

            response.failure = response.success
        }
    }
}

class RequestInfo {
    String xmlPage
    Boolean addIdToPngTemplate = true
}

class CecInfo {
    def xmlHttp
    def binaryHttp
    def pagePaths
    def rootFolder
    def xmlRootPath
}