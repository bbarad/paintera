package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageReader
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageWriter
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Writer
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.raw.n5.getReaderOrWriterIfN5Container
import org.janelia.saalfeldlab.paintera.state.raw.n5.urlRepresentation
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier
import java.util.function.ToIntFunction

private const val BASE_PATH = "basePath"

private class N5ReaderSerializer<N5 : N5Reader>(private val projectDirectory: Supplier<String>) : JsonSerializer<N5> {
    override fun serialize(
        container: N5,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        val projectDirectory = this.projectDirectory.get()
        return JsonObject()
            .also { m -> container.urlRepresentation().takeUnless { it == projectDirectory }?.let { m.addProperty(BASE_PATH, it) } }
    }
}

private class N5ReaderDeserializer<N5 : N5Reader>(
    private val projectDirectory: Supplier<String>,
    private val n5Constructor: (String) -> N5,
) : JsonDeserializer<N5> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): N5 {
        return with(GsonExtensions) {
            n5Constructor(json.getStringProperty(BASE_PATH) ?: projectDirectory.get())
        }
    }
}


//TODO Caleb: HDF5 is handled elsewhere; decide what to do about that (or nothing?)
private val classList = listOf(
    N5FSReader::class.java,
    N5AmazonS3Reader::class.java,
    N5GoogleCloudStorageReader::class.java,
    N5ZarrReader::class.java,
    N5FSWriter::class.java,
    N5AmazonS3Writer::class.java,
    N5GoogleCloudStorageWriter::class.java,
    N5ZarrWriter::class.java,
)


@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5FSReaderAdapter : StatefulSerializer.SerializerAndDeserializer<N5FSReader, JsonDeserializer<N5FSReader>, JsonSerializer<N5FSReader>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>,
    ): JsonSerializer<N5FSReader> = N5ReaderSerializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?,
    ): JsonDeserializer<N5FSReader> = N5ReaderDeserializer(projectDirectory) {
        getReaderOrWriterIfN5Container(it) as N5FSReader
    }

    override fun getTargetClass() = N5FSReader::class.java
}

@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5FSWriterAdapter : StatefulSerializer.SerializerAndDeserializer<N5FSWriter, JsonDeserializer<N5FSWriter>, JsonSerializer<N5FSWriter>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>,
    ): JsonSerializer<N5FSWriter> = N5ReaderSerializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?,
    ): JsonDeserializer<N5FSWriter> = N5ReaderDeserializer(projectDirectory) {
        N5Helpers.n5Writer(it) as N5FSWriter
    }

    override fun getTargetClass() = N5FSWriter::class.java
}


@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5GoogleCloudReaderAdapter : StatefulSerializer.SerializerAndDeserializer<N5GoogleCloudStorageReader, JsonDeserializer<N5GoogleCloudStorageReader>, JsonSerializer<N5GoogleCloudStorageReader>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>,
    ): JsonSerializer<N5GoogleCloudStorageReader> = N5ReaderSerializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?,
    ): JsonDeserializer<N5GoogleCloudStorageReader> = N5ReaderDeserializer(projectDirectory) {
        getReaderOrWriterIfN5Container(it) as N5GoogleCloudStorageReader
    }

    override fun getTargetClass() = N5GoogleCloudStorageReader::class.java
}

@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5GoogleCloudWriterAdapter : StatefulSerializer.SerializerAndDeserializer<N5GoogleCloudStorageWriter, JsonDeserializer<N5GoogleCloudStorageWriter>, JsonSerializer<N5GoogleCloudStorageWriter>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>,
    ): JsonSerializer<N5GoogleCloudStorageWriter> = N5ReaderSerializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?,
    ): JsonDeserializer<N5GoogleCloudStorageWriter> = N5ReaderDeserializer(projectDirectory) {
        N5Helpers.n5Writer(it) as N5GoogleCloudStorageWriter
    }

    override fun getTargetClass() = N5GoogleCloudStorageWriter::class.java
}

@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5AmazonS3ReaderAdapter : StatefulSerializer.SerializerAndDeserializer<N5AmazonS3Reader, JsonDeserializer<N5AmazonS3Reader>, JsonSerializer<N5AmazonS3Reader>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>,
    ): JsonSerializer<N5AmazonS3Reader> = N5ReaderSerializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?,
    ): JsonDeserializer<N5AmazonS3Reader> = N5ReaderDeserializer(projectDirectory) {
        getReaderOrWriterIfN5Container(it) as N5AmazonS3Reader
    }

    override fun getTargetClass() = N5AmazonS3Reader::class.java
}
