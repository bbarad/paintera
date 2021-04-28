/**
 * Copyright (c) 2018--2020, Saalfeld lab
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract class for single-scale or multi-scale N5 metadata.
 */
public abstract class AbstractN5DatasetMetadata<T extends N5DatasetMetadata> extends AbstractN5Metadata<T> implements N5DatasetMetadata {

  private DatasetAttributes attributes;

  public AbstractN5DatasetMetadata(final String path, final DatasetAttributes attributes) {

	super(path);
	this.attributes = attributes;
  }

  public AbstractN5DatasetMetadata(final String path) {

	this(path, null);
  }

  public AbstractN5DatasetMetadata(final DatasetAttributes attributes) {

	this("", attributes);
  }

  @Override
  public DatasetAttributes getAttributes() {

	return attributes;
  }

  public static abstract class AbstractN5DatasetMetadataParser<T extends N5DatasetMetadata> extends AbstractN5MetadataParser<T> {

	public static Map<String, Class<?>> datasetAtttributeKeys() {

	  final Map<String, Class<?>> defaultMap = new HashMap<String, Class<?>>();
	  addDatasetAttributeKeys(defaultMap);
	  return defaultMap;
	}

	public static void addDatasetAttributeKeys(final Map<String, Class<?>> keysToTypes) {

	  keysToTypes.put("dimensions", long[].class);
	  keysToTypes.put("blockSize", int[].class);
	  keysToTypes.put("dataType", String.class);
	}
  }

}
