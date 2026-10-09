package local.meli.dark

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatchContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream

/** Original XML is the light variant; only changed entries become Android night resources. */
internal fun ResourcePatchContext.snapshotLightResources(): Map<String, ByteArray> {
    val paths = MorpheSettingsResources::class.java.getResourceAsStream("/morphe-resource-paths.txt")!!.bufferedReader().readLines()
    if(paths.size != 54)throw PatchException("Selectable resource inventory changed")
    return paths.associateWith { get(it).readBytes() }
}

internal fun ResourcePatchContext.installSelectableResources(originals: Map<String, ByteArray>) {
    originals.forEach { (path,original) ->
        val dark=get(path).readBytes()
        val parts=path.split('/')
        val folder=parts[1]
        val nightFolder=if(folder.contains('-'))folder.substringBefore('-')+"-night-"+folder.substringAfter('-') else "$folder-night"
        val nightPath="res/$nightFolder/${parts[2]}"
        if(folder.startsWith("values")) {
            document(ByteArrayInputStream(original)).use { lightDoc ->
                document(ByteArrayInputStream(dark)).use { darkDoc ->
                    val originalNodes=(0 until lightDoc.documentElement.childNodes.length).mapNotNull { lightDoc.documentElement.childNodes.item(it) as? Element }
                    val darkNodes=(0 until darkDoc.documentElement.childNodes.length).mapNotNull { darkDoc.documentElement.childNodes.item(it) as? Element }
                    val changed=darkNodes.filter { candidate ->
                        val old=originalNodes.singleOrNull { it.tagName==candidate.tagName && it.getAttribute("name")==candidate.getAttribute("name") }
                        old==null || semanticNode(old)!=semanticNode(candidate)
                    }
                    val nightFile=get(nightPath,false)
                    if(!nightFile.exists()){nightFile.parentFile.mkdirs();nightFile.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources/>")}
                    document(nightPath).use { nightDoc ->
                        changed.forEach { node ->
                            val existing=(0 until nightDoc.documentElement.childNodes.length).mapNotNull { nightDoc.documentElement.childNodes.item(it) as? Element }
                                .filter { it.tagName==node.tagName && it.getAttribute("name")==node.getAttribute("name") }
                            existing.forEach { nightDoc.documentElement.removeChild(it) }
                            nightDoc.documentElement.appendChild(nightDoc.importNode(node,true))
                        }
                    }
                    // Light overlays retain the original parent, with no dark items.
                    darkNodes.filter { node -> originalNodes.none { it.tagName==node.tagName && it.getAttribute("name")==node.getAttribute("name") } }.forEach { node ->
                        val newNode=lightDoc.importNode(node,true) as Element
                        if(node.tagName=="style")while(newNode.hasChildNodes())newNode.removeChild(newNode.firstChild)
                        if(node.tagName=="color")newNode.textContent=if(node.getAttribute("name").endsWith("secondary"))"#666666" else "#333333"
                        lightDoc.documentElement.appendChild(newNode)
                    }
                    get(path).writeBytes(original)
                    // Document(InputStream) has no file destination; serialize light nodes explicitly.
                    document(path).use { output ->
                        while(output.documentElement.hasChildNodes())output.documentElement.removeChild(output.documentElement.firstChild)
                        (0 until lightDoc.documentElement.childNodes.length).forEach { i ->output.documentElement.appendChild(output.importNode(lightDoc.documentElement.childNodes.item(i),true))}
                    }
                }
            }
        } else {
            val target=get(nightPath,false);target.parentFile.mkdirs();target.writeBytes(dark)
            get(path).writeBytes(original)
        }
    }
    document("AndroidManifest.xml").use { doc ->
        val app=doc.getElementsByTagName("application").item(0) as Element
        val name="local.meli.morphe.SettingsActivity"
        if((0 until doc.getElementsByTagName("activity").length).any {(doc.getElementsByTagName("activity").item(it) as Element).getAttribute("android:name")==name})throw PatchException("Morphe Activity already exists")
        app.appendChild(doc.createElement("activity").apply {
            setAttribute("android:name",name);setAttribute("android:exported","false")
            setAttribute("android:label","Morphe")
            setAttribute("android:theme","@style/Theme.MLTheme")
        })
    }
}

private fun semanticNode(node: Element): String {
    val attrs=(0 until node.attributes.length).map { node.attributes.item(it).let { attr ->attr.nodeName+"="+attr.nodeValue } }.sorted().joinToString(";")
    val children=(0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? Element }
    return node.tagName+"["+attrs+"]"+if(children.isEmpty())node.textContent.trim() else children.joinToString(""){semanticNode(it)}
}
