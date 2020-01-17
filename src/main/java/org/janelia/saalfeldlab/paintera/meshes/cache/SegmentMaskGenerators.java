package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import bdv.viewer.Source;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.converter.Converter;
import net.imglib2.type.BooleanType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Util;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SegmentMaskGenerators
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static <T, B extends BooleanType<B>> BiFunction<TLongHashSet, Double, Converter<T, B>> create(
			final DataSource<T, ?> source,
			final int level)
	{
		final T t = source.getDataType();

		if (t instanceof LabelMultisetType)
			return new LabelMultisetTypeMaskGenerator(source, level);

		if (t instanceof IntegerType<?>)
		{
			final IntegerTypeMaskGenerator integerTypeMaskGenerator = new IntegerTypeMaskGenerator();
			return (l, minLabelRatio) -> integerTypeMaskGenerator.apply(l);
		}

		return null;
	}

	private static class LabelMultisetTypeMaskGenerator<B extends BooleanType<B>>
			implements BiFunction<TLongHashSet, Double, Converter<LabelMultisetType, B>>
	{
		private final long numFullResPixels;

		LabelMultisetTypeMaskGenerator(final Source<?> source, final int level)
		{
			final double[] scales = DataSource.getRelativeScales(source, 0, 0, level);
			// check that all scales are integers
			assert Arrays.stream(scales).allMatch(scale -> Util.isApproxEqual(scale, Math.round(scale), 1e-7));
			numFullResPixels = Arrays.stream(scales).mapToLong(Math::round).reduce(1, Math::multiplyExact);
		}

		@Override
		public Converter<LabelMultisetType, B> apply(final TLongHashSet validLabels, final Double minLabelRatio)
		{
			return new LabelMultisetTypeMask<>(validLabels, minLabelRatio, numFullResPixels);
		}

	}

	private static class IntegerTypeMaskGenerator<I extends IntegerType<I>, B extends BooleanType<B>>
			implements Function<TLongHashSet, Converter<I, B>>
	{

		@Override
		public Converter<I, B> apply(final TLongHashSet validLabels)
		{
			return new IntegerTypeMask<>(validLabels);
		}

	}

	private static class LabelMultisetTypeMask<B extends BooleanType<B>> implements Converter<LabelMultisetType, B>
	{

		private final TLongHashSet validLabels;
		private final Double minLabelRatio;
		private final long numFullResPixels;

		public LabelMultisetTypeMask(final TLongHashSet validLabels, final Double minLabelRatio, final long numFullResPixels)
		{
			assert numFullResPixels > 0;
			this.validLabels = validLabels;
			this.minLabelRatio = minLabelRatio;
			this.numFullResPixels = numFullResPixels;
		}

		@Override
		public void convert(final LabelMultisetType input, final B output)
		{
			final Set<Entry<Label>> inputSet        = input.entrySet();
			final int               validLabelsSize = validLabels.size();
			final int               inputSize       = inputSet.size();
			// no primitive type support for slf4j
			// http://mailman.qos.ch/pipermail/slf4j-dev/2005-August/000241.html
			if (LOG.isTraceEnabled())
			{
				LOG.trace("input size={}, validLabels size={}", inputSize, validLabelsSize);
			}

			if (minLabelRatio == null || minLabelRatio <= 0.0)
			{
				// basic implementation that only checks if any of the labels are contained in the current pixel
				if (validLabelsSize < inputSize)
				{
					for (final TLongIterator it = validLabels.iterator(); it.hasNext(); )
					{
						if (input.contains(it.next()))
						{
							output.set(true);
							return;
						}
					}
				}
				else
				{
					for (final Iterator<Entry<Label>> it = inputSet.iterator(); it.hasNext(); )
					{
						if (validLabels.contains(it.next().getElement().id()))
						{
							output.set(true);
							return;
						}
					}
				}
				output.set(false);
			}
			else
			{
				// Count occurrences of the labels in the current pixel to see if it's above the specified min label pixel ratio
				long validLabelsContainedCount = 0;
				for (final TLongIterator it = validLabels.iterator(); it.hasNext(); )
					validLabelsContainedCount += input.count(it.next());

				if (validLabelsContainedCount == 0)
					output.set(false);
				else
					output.set((double) validLabelsContainedCount / numFullResPixels >= minLabelRatio);
			}
		}
	}

	private static class IntegerTypeMask<I extends IntegerType<I>, B extends BooleanType<B>> implements Converter<I, B>
	{

		private final TLongHashSet validLabels;

		public IntegerTypeMask(final TLongHashSet validLabels)
		{
			super();
			this.validLabels = validLabels;
		}

		@Override
		public void convert(final I input, final B output)
		{
			output.set(validLabels.contains(input.getIntegerLong()));
		}

	}

}
