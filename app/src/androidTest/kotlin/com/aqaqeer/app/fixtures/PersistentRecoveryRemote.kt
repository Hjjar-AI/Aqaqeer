package com.aqaqeer.app.fixtures

import com.aqaqeer.app.admin.*
import com.aqaqeer.app.sync.*
import kotlinx.serialization.json.*
import java.util.Base64

/** A simulated remote surviving JVM death. Only encrypted snapshot bytes are retained. */
internal class PersistentRecoveryRemote(private val profile: RestoreCrashProfile, private val gate: RestoreCrashGate? = null):
    TelegramSetupTransport,SnapshotWriteTransport,SnapshotReadTransport {
    private val delegate=SuiteRemote()
    val title get()=delegate.title
    val uploads get()=delegate.uploads
    val writes get()=delegate.writes
    init {
        val bytes=profile.real.read("acceptance-remote")
        try { if(bytes!=null) {
            val state=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            delegate.uploads=state.getValue("uploads").jsonPrimitive.int;delegate.writes=state.getValue("writes").jsonPrimitive.int
            state.getValue("files").jsonObject.forEach { (name,value) -> delegate.files[name]=Base64.getDecoder().decode(value.jsonPrimitive.content) }
            state.getValue("captions").jsonObject.forEach { (name,value) -> delegate.captions[name.toLong()]=SnapshotCaption.decode(value.jsonPrimitive.content) }
            state["current"]?.takeUnless { it==JsonNull }?.jsonObject?.let {
                delegate.current=SnapshotDocument(it.getValue("group").jsonPrimitive.long,it.getValue("message").jsonPrimitive.long,
                    it.getValue("file").jsonPrimitive.content,it.getValue("unique").jsonPrimitive.content,SnapshotCaption.decode(it.getValue("caption").jsonPrimitive.content))
            }
        } } finally { bytes?.fill(0) }
    }
    private fun persist() {
        val value=buildJsonObject {
            put("uploads",uploads);put("writes",writes)
            put("files",JsonObject(delegate.files.mapValues { JsonPrimitive(Base64.getEncoder().encodeToString(it.value)) }))
            put("captions",JsonObject(delegate.captions.mapKeys { it.key.toString() }.mapValues { JsonPrimitive(SnapshotCaption.encode(it.value)) }))
            put("current",delegate.current?.let { d -> buildJsonObject {
                put("group",d.groupId);put("message",d.messageId);put("file",d.fileId);put("unique",d.fileUniqueId);put("caption",SnapshotCaption.encode(d.publication))
            } } ?: JsonNull)
        }.toString().toByteArray()
        try { profile.real.write("acceptance-remote",value) } finally { value.fill(0) }
    }
    override suspend fun validate(token:String,groupId:Long)=delegate.validate(token,groupId)
    override suspend fun discover(token:String,challenge:String)=delegate.discover(token,challenge)
    override suspend fun inspect(token:String,group:Long)=delegate.inspect(token,group)
    override suspend fun upload(token:String,group:Long,bytes:ByteArray,caption:String)=delegate.upload(token,group,bytes,caption).also { persist() }
    override suspend fun replace(token:String,group:Long,message:Long,file:String,caption:String) {
        delegate.replace(token,group,message,file,caption);persist();gate?.hit("maintenance_after_remote_write")
    }
    override suspend fun pin(token:String,group:Long,message:Long) {
        delegate.pin(token,group,message);persist();gate?.hit("maintenance_after_remote_write")
    }
    override suspend fun bot(token:String)=delegate.bot(token)
    override suspend fun pinned(token:String,groupId:Long,bot:BotIdentity)=delegate.pinned(token,groupId,bot)
    override suspend fun document(token:String,document:SnapshotDocument)=delegate.document(token,document)
}
