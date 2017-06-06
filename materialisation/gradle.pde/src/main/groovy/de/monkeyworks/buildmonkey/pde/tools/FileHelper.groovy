package de.monkeyworks.buildmonkey.pde.tools

/**
 * Created by micha on 02.06.17.
 */
class FileHelper {

    static File findSubFolder(File parent, String folderName) {
        if(parent == null || !parent.exists()) {
            return
        }
        File retval = null
        def files = parent.listFiles()
        files.each {
            if(it.isDirectory() && it.getName().equals(folderName)) {
                retval = it
            } else {
                File tmp = findSubFolder(it, folderName)
                if(tmp != null)
                    retval = tmp
            }
        }

        return retval
    }
}
