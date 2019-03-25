/**
 * Handle after create events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the original item being created.
 */

import org.artifactory.fs.FileInfo
import org.artifactory.fs.ItemInfo

storage {
    afterCreate { item ->
        toFile(item)
    }
}

def toFile(ItemInfo item) {
    File f = new File("/tmp/test1.log")
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
    def checksums = "None"
    if (item instanceof FileInfo) {
        checksums = (item as FileInfo).getSha2()
    }

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
            "is folder: " + isFolder + "\n" +
            "sh256: " + checksums)
}

