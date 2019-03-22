/**
 * Handle after create events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the original item being created.
 */
import org.artifactory.fs.ItemInfo

storage {
    afterCreate { item ->
        toFile(item)
    }
}

def toFile(ItemInfo item) {
    File f = new File("/tmp/test1.log")
    def name = item.getName()
    f.write name
}

