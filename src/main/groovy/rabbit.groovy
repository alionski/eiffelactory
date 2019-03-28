/**
 * Handle after create events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the original item being created.
 */

import org.artifactory.fs.FileInfo
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import helloworld.*
import rabbitmq2.*

class Outer {
    def File logfile = new File("/tmp/rabbit.log")
    RecvMQ recv = new RecvMQ()
    SendMQ send = new SendMQ()

    def startSender() {
        new Thread(new Runnable() {
            void run() {
                while (true) {
                    def str = send.send()
                    logfile.append(str + "\n")
                    Thread.sleep(5000)
                }
            }
        }).start()
    }

    def startReceiver() {
        new Thread(new Runnable() {
            void run() {
                while (true) {
                    recv.startReceiving()
                    Thread.sleep(5000)
                 }
            }
        }).start()
    }

}

Outer outer = new Outer()
outer.startSender()
outer.startReceiver()

storage {
    afterCreate { item ->
        hello()
        //toFile(item)
    }
}

def hello() {
    Hello hello = new Hello()
    def str = hello.hello()
    File f = new File("/tmp/hello.log")	   
    f.write str
}

def toFile(ItemInfo item) {
    File f = new File("/tmp/test.log")
    def created = item.getCreated() as String
    def createdBy = item.getCreatedBy()
    def id = item.getId() as String
    def lastModified = item.getLastModified() as String
    def lastUpd = item.getLastUpdated() as String
    def modifiedBy = item.getModifiedBy() as String
    def name = item.getName()
    def relPath = item.getRelPath()
    def repoKey = item.getRepoKey()
    def repoPath = item.getRepoPath().toPath()
    def isFolder = item.isFolder() as String

    f.write("Name: " + name + "\n" +
            "Created: " + created + "\n" +
            "Created by: " + createdBy  + "\n" +
            "Id: " + id  + "\n" +
            "Last mod: " + lastModified  + "\n" +
            "last upd: " + lastUpd + "\n" +
            "Mod by: " + modifiedBy  + "\n" +
            "Rel path: " + relPath  + "\n" +
            "Repo key: " +repoKey  + "\n" +
            "Repo path: " + repoPath  + "\n" +
            "is folder: " + isFolder + "\n" )

    if (item instanceof FileInfo) {
        def fileInfo = item as FileInfo
        def sha256 = fileInfo.getSha2()
        def sha1 = fileInfo.getSha1()
        def md5 = fileInfo.getMd5()
        def mime = fileInfo.getMimeType()
        def age = fileInfo.getAge() as String
        def size = fileInfo.getSize()

        f.append("sha256: " + sha256 + "\n" +
                "sha1: " + sha1 + "\n" +
                "md5: " + md5 + "\n" +
                "Mime: " + mime + "\n" +
                "Age: "  + age + "\n" +
                "Size: " + size + "\n")
    }
    
    if (item instanceof FileLayoutInfo) {
        def fileLayout = item as FileLayoutInfo

        f.append("base revision: " + fileLayout.getBaseRevision() + "\n" +
                "Classifier : " + fileLayout.getClassifier() + "\n" +
                "Ext : " + fileLayout.getExt() + "\n")
    }
}

