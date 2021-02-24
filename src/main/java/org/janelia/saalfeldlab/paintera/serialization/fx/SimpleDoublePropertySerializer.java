package org.janelia.saalfeldlab.paintera.serialization.fx;

import java.lang.reflect.Type;
import java.util.Optional;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import javafx.beans.property.SimpleDoubleProperty;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class SimpleDoublePropertySerializer implements PainteraSerialization.PainteraAdapter<SimpleDoubleProperty> {

  @Override
  public SimpleDoubleProperty deserialize(final JsonElement json, final Type typeOfT, final
  JsonDeserializationContext context)
		  throws JsonParseException {

	return new SimpleDoubleProperty(Optional.ofNullable((Double)context.deserialize(
			json,
			double.class
	)).orElse(0.0));
  }

  @Override
  public JsonElement serialize(final SimpleDoubleProperty src, final Type typeOfSrc, final JsonSerializationContext context) {

	return new JsonPrimitive(src.get());
  }

  @Override
  public Class<SimpleDoubleProperty> getTargetClass() {

	return SimpleDoubleProperty.class;
  }
}
