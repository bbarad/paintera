package org.janelia.saalfeldlab.util.n5;

import bdv.viewer.Interpolation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ScaleAndTranslation;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultiset;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.N5CacheLoader;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupAdapter;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupFromFile;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.LongArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5CellLoader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.cache.Invalidate;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.cache.global.InvalidAccessException;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal.Persister;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.UnableToPersist;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrderNotSupported;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5FSMeta;
import org.janelia.saalfeldlab.paintera.data.n5.N5HDF5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.ReflectionException;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.paintera.ui.opendialog.VolatileHelpers;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class N5Helpers
{

	public static final String MULTI_SCALE_KEY = "multiScale";

	public static final String IS_LABEL_MULTISET_KEY = "isLabelMultiset";

	public static final String MAX_ID_KEY = "maxId";

	public static final String RESOLUTION_KEY = "resolution";

	public static final String OFFSET_KEY = "offset";

	public static final String DOWNSAMPLING_FACTORS_KEY = "downsamplingFactors";

	public static final String LABEL_MULTISETTYPE_KEY = "isLabelMultiset";

	public static final String MAX_NUM_ENTRIES_KEY = "maxNumEntries";

	public static final String PAINTERA_DATA_KEY = "painteraData";

	public static final String PAINTERA_DATA_DATASET = "data";

	public static final String PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASTE = "fragment-segment-assignment";

	public static final String LABEL_TO_BLOCK_MAPPING = "label-to-block-mapping";

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class ImagesWithInvalidate<D, T> {
		public final RandomAccessibleInterval<D> data;

		public final RandomAccessibleInterval<T> vdata;

		public final AffineTransform3D transform;

		public final Invalidate<Long> invalidate;

		public final Invalidate<Long> vinvalidate;

		public ImagesWithInvalidate(
				final RandomAccessibleInterval<D> data,
				final RandomAccessibleInterval<T> vdata,
				final AffineTransform3D transform,
				final Invalidate<Long> invalidate,
				final Invalidate<Long> vinvalidate) {
			this.data = data;
			this.vdata = vdata;
			this.transform = transform;
			this.invalidate = invalidate;
			this.vinvalidate = vinvalidate;
		}
	}

	/**
	 * Check if a group is a paintera data set:
	 * @param n5 {@link N5Reader} container
	 * @param group to be tested for paintera dataset
	 * @return {@code true} if {@code group} exists and has attribute {@code painteraData}.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static boolean isPainteraDataset(final N5Reader n5, final String group) throws IOException
	{
		return n5.exists(group) && n5.listAttributes(group).containsKey(PAINTERA_DATA_KEY);
	}

	/**
	 * Determine if a group is multiscale.
	 * @param n5 {@link N5Reader} container
	 * @param group to be tested for multiscale
	 * @return {@code true} if {@code group} exists and is not a dataset and has attribute "multiScale": true,
	 * or (legacy) all children are groups following the regex pattern {@code "$s[0-9]+^"}, {@code false} otherwise.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static boolean isMultiScale(final N5Reader n5, final String group) throws IOException
	{

		if (!n5.exists(group) || n5.datasetExists(group))
			return false;

		/* based on attribute */
		boolean isMultiScale = Optional.ofNullable(n5.getAttribute(group, MULTI_SCALE_KEY, Boolean.class)).orElse(false);

		/*
		 * based on groupd content (the old way) TODO conider removing as
		 * multi-scale declaration by attribute becomes part of the N5 spec.
		 */
		if (!isMultiScale && !n5.datasetExists(group))
		{
			final String[] subGroups = n5.list(group);
			isMultiScale = subGroups.length > 0;
			for (final String subGroup : subGroups)
			{
				if (!(subGroup.matches("^s[0-9]+$") && n5.datasetExists(group + "/" + subGroup)))
				{
					isMultiScale = false;
					break;
				}
			}
			if (isMultiScale)
			{
				LOG.debug(
						"Found multi-scale group without {} tag. Implicit multi-scale detection will be removed in " +
								"the" +
								" future. Please add \"{}\":{} to attributes.json.",
						MULTI_SCALE_KEY,
						MULTI_SCALE_KEY,
						true
				        );
			}
		}
		return isMultiScale;
	}


	/**
	 * List all scale datasets within {@code group}
	 * @param n5 {@link N5Reader} container
	 * @param group contains scale directories
	 * @return list of all contained scale datasets, relative to {@code group},
	 * e.g. for a structure {@code "group/{s0,s1}"} this would return {@code {"s0", "s1"}}.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static String[] listScaleDatasets(final N5Reader n5, final String group) throws IOException
	{
		final String[] scaleDirs = Arrays
				.stream(n5.list(group))
				.filter(s -> s.matches("^s\\d+$"))
				.filter(s -> {
					try
					{
						return n5.datasetExists(group + "/" + s);
					} catch (final IOException e)
					{
						return false;
					}
				})
				.toArray(String[]::new);

		LOG.debug("Found these scale dirs: {}", Arrays.toString(scaleDirs));
		return scaleDirs;
	}


	/**
	 * List and sort all scale datasets within {@code group}
	 * @param n5 {@link N5Reader} container
	 * @param group contains scale directories
	 * @return sorted list of all contained scale datasets, relative to {@code group},
	 * e.g. for a structure {@code "group/{s0,s1}"} this would return {@code {"s0", "s1"}}.
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static String[] listAndSortScaleDatasets(final N5Reader n5, final String group) throws IOException
	{
		final String[] scaleDirs = listScaleDatasets(n5, group);
		sortScaleDatasets(scaleDirs);

		LOG.debug("Sorted scale dirs: {}", Arrays.toString(scaleDirs));
		return scaleDirs;
	}

	/**
	 *
	 * @param n5 {@link N5Reader} container
	 * @param group multi-scale group, dataset, or paintera dataset
	 * @return {@link DatasetAttributes} found in appropriate {@code attributes.json}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static DatasetAttributes getDatasetAttributes(final N5Reader n5, final String group) throws IOException
	{
		LOG.debug("Getting data type for group/dataset {}", group);
		if (isPainteraDataset(n5, group)) { return getDatasetAttributes(n5, group + "/" + PAINTERA_DATA_DATASET); }
		if (isMultiScale(n5, group)) { return getDatasetAttributes(n5, getFinestLevel(n5, group)); }
		return n5.getDatasetAttributes(group);
	}

	/**
	 * Sort scale datasets numerically by removing all non-number characters during comparison.
	 * @param scaleDatasets list of scale datasets
	 */
	public static void sortScaleDatasets(final String[] scaleDatasets)
	{
		Arrays.sort(scaleDatasets, Comparator.comparingInt(s -> Integer.parseInt(s.replaceAll("[^\\d]", ""))));
	}

	/**
	 *
	 * @param base path to directory or h5 file
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Reader} for file system or h5 access
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static N5Reader n5Reader(final String base, final int... defaultCellDimensions) throws IOException
	{
		return isHDF(base) ? new N5HDF5Reader(base, defaultCellDimensions) : new N5FSReader(base);
	}

	/**
	 *
	 * @param base path to directory or h5 file
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Reader} with custom {@link GsonBuilder} for file system or h5 access
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static N5Reader n5Reader(final String base, final GsonBuilder gsonBuilder, final int... defaultCellDimensions)
	throws IOException
	{
		return isHDF(base) ? new N5HDF5Reader(base, defaultCellDimensions) : new N5FSReader(base, gsonBuilder);
	}

	/**
	 *
	 * @param base path to directory or h5 file
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Writer} for file system or h5 access
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static N5Writer n5Writer(final String base, final int... defaultCellDimensions) throws IOException
	{
		return isHDF(base) ? new N5HDF5Writer(base, defaultCellDimensions) : new N5FSWriter(base);
	}

	/**
	 *
	 * @param base path to directory or h5 file
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Writer} with custom {@link GsonBuilder} for file system or h5 access
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static N5Writer n5Writer(final String base, final GsonBuilder gsonBuilder, final int...
			defaultCellDimensions)
	throws IOException
	{
		return isHDF(base) ? new N5HDF5Writer(base, defaultCellDimensions) : new N5FSWriter(base, gsonBuilder);
	}

	/**
	 * Generate {@link N5Meta} from base path
	 * @param base base path of n5 container
	 * @param dataset dataset
	 * @param defaultCellDimensions default cell dimensions (only required for h5 readers)
	 * @return appropriate {@link N5Meta} object for file system or h5 access
	 */
	@SuppressWarnings("unused")
	public static N5Meta metaData(final String base, final String dataset, final int... defaultCellDimensions)
	{
		return isHDF(base) ? new N5HDF5Meta(base, dataset, defaultCellDimensions, false) : new N5FSMeta(base, dataset);
	}

	/**
	 *
	 * @param base path
	 * @return {@code true} if {@code base} starts with "h5://" or ends with ".hdf5" or ".h5"
	 */
	public static boolean isHDF(final String base)
	{
		LOG.debug("Checking {} for HDF", base);
		final boolean isHDF = Pattern.matches("^h5://", base) || Pattern.matches("^.*\\.(hdf|h5)$", base);
		LOG.debug("{} is hdf5? {}", base, isHDF);
		return isHDF;
	}

	/**
	 * Find all datasets inside an n5 container
	 * A dataset is any one of:
	 *   - N5 dataset
	 *   - multi-sclae group
	 *   - paintera dataset
	 * @param n5 container
	 * @param onInterruption run this action when interrupted
	 * @return List of all contained datasets (paths wrt to the root of the container)
	 */
	public static List<String> discoverDatasets(final N5Reader n5, final Runnable onInterruption)
	{
		final List<String> datasets = new ArrayList<>();
		final ExecutorService exec = Executors.newFixedThreadPool(
				n5 instanceof N5HDF5Reader ? 1 : 12,
				new NamedThreadFactory("dataset-discovery-%d", true)
		                                                         );
		final AtomicInteger counter = new AtomicInteger(1);
		exec.submit(() -> discoverSubdirectories(n5, "", datasets, exec, counter));
		while (counter.get() > 0 && !Thread.currentThread().isInterrupted())
		{
			try
			{
				Thread.sleep(20);
			} catch (final InterruptedException e)
			{
				exec.shutdownNow();
				onInterruption.run();
			}
		}
		exec.shutdown();
		Collections.sort(datasets);
		return datasets;
	}

	private static void discoverSubdirectories(
			final N5Reader n5,
			final String pathName,
			final Collection<String> datasets,
			final ExecutorService exec,
			final AtomicInteger counter)
	{
		try
		{
			if (isPainteraDataset(n5, pathName))
			{
				synchronized (datasets)
				{
					datasets.add(pathName);
				}
			}
			else if (n5.datasetExists(pathName))
			{
				synchronized (datasets)
				{
					datasets.add(pathName);
				}
			}
			else
			{

				String[] groups = null;
				/* based on attribute */

				boolean isMipmapGroup = Optional.ofNullable(n5.getAttribute(
						pathName,
						MULTI_SCALE_KEY,
						Boolean.class
				                                                           )).orElse(false);

				/* based on groupd content (the old way) */
				if (!isMipmapGroup)
				{
					groups = n5.list(pathName);
					isMipmapGroup = groups.length > 0;
					for (final String group : groups)
					{
						if (!(group.matches("^s[0-9]+$") && n5.datasetExists(pathName + "/" + group)))
						{
							isMipmapGroup = false;
							break;
						}
					}
					if (isMipmapGroup)
					{
						LOG.debug(
								"Found multi-scale group without {} tag. Implicit multi-scale detection will be " +
										"removed in the future. Please add \"{}\":{} to attributes.json.",
								MULTI_SCALE_KEY,
								MULTI_SCALE_KEY,
								true
						        );
					}
				}
				if (isMipmapGroup)
				{
					synchronized (datasets)
					{
						datasets.add(pathName);
					}
				}
				else
				{
					for (final String group : groups)
					{
						final String groupPathName = pathName + "/" + group;
						final int    numThreads    = counter.incrementAndGet();
						LOG.debug("entering {}, {} threads created", groupPathName, numThreads);
						exec.submit(() -> discoverSubdirectories(n5, groupPathName, datasets, exec, counter));
					}
				}
			}
		} catch (final IOException e)
		{
			LOG.debug(e.toString(), e);
		}
		final int numThreads = counter.decrementAndGet();
		LOG.debug("leaving {}, {} threads remaining", pathName, numThreads);
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return image data and cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V> openRaw(
			final N5Reader reader,
			final String dataset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return openRaw(
				reader,
				dataset,
				getResolution(reader, dataset),
				getOffset(reader, dataset),
				globalCache,
				priority
		              );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param axisOrder set axis order for this data
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param name initialize with this name
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static <T extends NativeType<T> & RealType<T>, V extends Volatile<T> & NativeType<V> & RealType<V>>
	DataSource<T, V> openRawAsSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final AxisOrder axisOrder,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws IOException, ReflectionException, AxisOrderNotSupported {
		return openScalarAsSource(
				reader,
				dataset,
				transform,
				axisOrder,
				globalCache,
				priority,
				i -> i == Interpolation.NLINEAR
				     ? new NLinearInterpolatorFactory<>()
				     : new NearestNeighborInterpolatorFactory<>(),
				i -> i == Interpolation.NLINEAR
				     ? new NLinearInterpolatorFactory<>()
				     : new NearestNeighborInterpolatorFactory<>(),
				name
		                         );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param axisOrder set axis order for this data
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param name initialize with this name
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	DataSource<T, V> openScalarAsSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final AxisOrder axisOrder,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws IOException, ReflectionException, AxisOrderNotSupported {
		return openScalarAsSource(
				reader,
				dataset,
				transform,
				axisOrder,
				globalCache,
				priority,
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				name
		                         );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param axisOrder set axis order for this data
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param dataInterpolation interpolator factory for data
	 * @param interpolation interpolator factory for viewer data
	 * @param name initialize with this name
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	DataSource<T, V> openScalarAsSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final AxisOrder axisOrder,
			final GlobalCache globalCache,
			final int priority,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<V, RandomAccessible<V>>> interpolation,
			final String name) throws IOException, ReflectionException, AxisOrderNotSupported {

		LOG.debug("Creating N5 Data source from {} {}", reader, dataset);
		return new N5DataSource<>(
				Objects.requireNonNull(N5Meta.fromReader(reader, dataset)),
				transform,
				axisOrder,
				globalCache,
				name,
				priority,
				dataInterpolation,
				interpolation
		);
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V>[] openScalar(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return isMultiScale(reader, dataset)
		       ? openRawMultiscale(reader, dataset, transform, globalCache, priority)
		       : new ImagesWithInvalidate[] {openRaw(reader, dataset, transform, globalCache, priority)};

	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V> openRaw(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		             );
		return openRaw(reader, dataset, transform, globalCache, priority);
	}


	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unchecked")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>, A extends ArrayDataAccess<A>>
	ImagesWithInvalidate<T, V> openRaw(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException {

		try {
			final CellGrid grid = getGrid(reader, dataset);
			final CellLoader<T> loader = new N5CellLoader<>(reader, dataset, reader.getDatasetAttributes(dataset).getBlockSize());
			final T type = N5Types.type(reader.getDatasetAttributes(dataset).getDataType());
			final Pair<CachedCellImg<T, A>, Invalidate<Long>> raw = globalCache.createVolatileImg(grid, loader, type);
			final Triple<RandomAccessibleInterval<V>, VolatileCache<Long, Cell<A>>, Invalidate<Long>> vraw = globalCache.wrapAsVolatile(raw.getA(), raw.getB(), priority);
			return new ImagesWithInvalidate<>(raw.getA(), vraw.getA(), transform, raw.getB(), vraw.getC());
		}
		catch (Exception e)
		{
			throw e instanceof IOException ? (IOException) e : new IOException(e);
		}
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V>[] openRawMultiscale(
			final N5Reader reader,
			final String dataset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return openRawMultiscale(
				reader,
				dataset,
				getResolution(reader, dataset),
				getOffset(reader, dataset),
				globalCache,
				priority
		                        );

	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param resolution voxel resolution
	 * @param offset offset in real world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param <T> data type
	 * @param <V> viewer type
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V>[] openRawMultiscale(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		             );
		return openRawMultiscale(reader, dataset, transform, globalCache, priority);
	}

	@SuppressWarnings("unchecked")
	public static <T extends NativeType<T>, V extends Volatile<T> & NativeType<V>>
	ImagesWithInvalidate<T, V>[] openRawMultiscale(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(reader, dataset);

		LOG.debug("Opening directories {} as multi-scale in {}: ", Arrays.toString(scaleDatasets), dataset);

		final double[] initialDonwsamplingFactors = getDownsamplingFactors(
				reader,
				Paths.get(dataset, scaleDatasets[0]).toString()
		                                                                  );
		LOG.debug("Initial transform={}", transform);
		final ExecutorService es = Executors.newFixedThreadPool(
				scaleDatasets.length,
				new NamedThreadFactory("populate-mipmap-scales-%d", true)
		                                                       );
		final ArrayList<Future<Boolean>> futures = new ArrayList<>();
		final ImagesWithInvalidate<T, V>[] imagesWithInvalidate = new ImagesWithInvalidate[scaleDatasets.length];
		for (int scale = 0; scale < scaleDatasets.length; ++scale)
		{
			final int fScale = scale;
			futures.add(es.submit(MakeUnchecked.supplier(() -> {
				LOG.debug("Populating scale level {}", fScale);
				final String scaleDataset = Paths.get(dataset, scaleDatasets[fScale]).toString();
				imagesWithInvalidate[fScale] = openRaw(reader, scaleDataset, transform.copy(), globalCache, priority);
				final double[] downsamplingFactors = getDownsamplingFactors(reader, scaleDataset);
				LOG.debug("Read downsampling factors: {}", Arrays.toString(downsamplingFactors));
				imagesWithInvalidate[fScale].transform.set(considerDownsampling(
						imagesWithInvalidate[fScale].transform.copy(),
						downsamplingFactors,
						initialDonwsamplingFactors));
				LOG.debug("Populated scale level {}", fScale);
				return true;
			})::get));
		}
		futures.forEach(MakeUnchecked.unchecked(Future::get));
		es.shutdown();
		return imagesWithInvalidate;
	}


	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param axisOrder set axis order for this data
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param name initialize with this name
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static DataSource<LabelMultisetType, VolatileLabelMultisetType>
	openLabelMultisetAsSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final AxisOrder axisOrder,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws IOException, ReflectionException, AxisOrderNotSupported {
		return new N5DataSource<>(
				Objects.requireNonNull(N5Meta.fromReader(reader, dataset)),
				transform,
				axisOrder,
				globalCache,
				name,
				priority,
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>()
		);
	}


	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType> openLabelMultiset(
			final N5Reader reader,
			final String dataset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return openLabelMultiset(
				reader,
				dataset,
				getResolution(reader, dataset),
				getOffset(reader, dataset),
				globalCache,
				priority
		                        );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param resolution voxel size
	 * @param offset in world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType> openLabelMultiset(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		             );
		return openLabelMultiset(reader, dataset, transform, globalCache, priority);
	}

	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType> openLabelMultiset(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		try {
			final DatasetAttributes attrs = reader.getDatasetAttributes(dataset);
			final N5CacheLoader loader = new N5CacheLoader(
					reader,
					dataset,
					N5CacheLoader.constantNullReplacement(Label.BACKGROUND)
			);
			final Pair<CachedCellImg<LabelMultisetType, VolatileLabelMultisetArray>, Invalidate<Long>> cachedImg = globalCache.createImg(
					new CellGrid(attrs.getDimensions(), attrs.getBlockSize()),
					loader,
					new LabelMultisetType().getEntitiesPerPixel(),
					new VolatileLabelMultisetArray(0, true, new long[]{Label.INVALID})
			);
			cachedImg.getA().setLinkedType(new LabelMultisetType(cachedImg.getA()));


			@SuppressWarnings("unchecked")
			final Function<NativeImg<VolatileLabelMultisetType, ? extends VolatileLabelMultisetArray>, VolatileLabelMultisetType> linkedTypeFactory =
					img -> new VolatileLabelMultisetType((NativeImg<?, VolatileLabelMultisetArray>) img);

			Triple<RandomAccessibleInterval<VolatileLabelMultisetType>, VolatileCache<Long, Cell<VolatileLabelMultisetArray>>, Invalidate<Long>> vimg = globalCache.wrapAsVolatile(
					cachedImg.getA(),
					cachedImg.getB(),
					linkedTypeFactory,
					new VolatileHelpers.CreateInvalidVolatileLabelMultisetArray(cachedImg.getA().getCellGrid()),
					priority);

			return new ImagesWithInvalidate<>(cachedImg.getA(), vimg.getA(), transform, cachedImg.getB(), vimg.getC());
		}
		catch (InvalidAccessException e)
		{
			throw new IOException(e);
		}

	}


	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unused")
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType>[] openLabelMultisetMultiscale(
			final N5Reader reader,
			final String dataset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		return openLabelMultisetMultiscale(
				reader,
				dataset,
				getResolution(reader, dataset),
				getOffset(reader, dataset),
				globalCache,
				priority
		                                  );
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param resolution voxel size
	 * @param offset in world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType>[] openLabelMultisetMultiscale(
			final N5Reader reader,
			final String dataset,
			final double[] resolution,
			final double[] offset,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		             );
		return openLabelMultisetMultiscale(
				reader,
				dataset,
				transform,
				globalCache,
				priority
		                                  );
	}


	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform from voxel space to world coordinates
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @return multi-scale image data with cache invalidation
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unchecked")
	public static ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType>[] openLabelMultisetMultiscale(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final GlobalCache globalCache,
			final int priority) throws IOException
	{
		final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets(reader, dataset);

		LOG.debug(
				"Opening directories {} as multi-scale in {} and transform={}: ",
				Arrays.toString(scaleDatasets),
				dataset,
				transform
		         );

		final double[] initialDonwsamplingFactors = getDownsamplingFactors(
				reader,
				Paths.get(dataset, scaleDatasets[0]).toString()
		                                                                  );
		final ExecutorService es = Executors.newFixedThreadPool(
				scaleDatasets.length,
				new NamedThreadFactory("populate-mipmap-scales-%d", true)
		                                                       );
		final ArrayList<Future<Boolean>> futures = new ArrayList<>();
		final ImagesWithInvalidate<LabelMultisetType, VolatileLabelMultisetType>[] imagesWithInvalidate = new ImagesWithInvalidate[scaleDatasets.length];
		for (int scale = 0; scale < scaleDatasets.length; ++scale)
		{
			final int fScale = scale;
			futures.add(es.submit(MakeUnchecked.supplier(() -> {
				LOG.debug("Populating scale level {}", fScale);
				final String scaleDataset = Paths.get(dataset, scaleDatasets[fScale]).toString();
				imagesWithInvalidate[fScale] = openLabelMultiset(reader, scaleDataset, transform.copy(), globalCache, priority);
				final double[] downsamplingFactors = getDownsamplingFactors(reader, scaleDataset);
				LOG.debug("Read downsampling factors: {}", Arrays.toString(downsamplingFactors));
				imagesWithInvalidate[fScale].transform.set(considerDownsampling(
						imagesWithInvalidate[fScale].transform.copy(),
						downsamplingFactors,
						initialDonwsamplingFactors));
				LOG.debug("Populated scale level {}", fScale);
				return true;
			})::get));
		}
		futures.forEach(MakeUnchecked.unchecked(Future::get));
		es.shutdown();
		return imagesWithInvalidate;
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @param transform transforms voxel data into real world coordinates
	 * @param axisOrder set axis order for this data
	 * @param globalCache {@link GlobalCache} to create sub-cache for this dataset
	 * @param priority in fetching queue
	 * @param name initialize with this name
	 * @param <D> data type
	 * @param <T> viewer type
	 * @return {@link DataSource}
	 * @throws IOException if any N5 operation throws {@link IOException}
	 */
	@SuppressWarnings("unchecked")
	public static <D extends NativeType<D>, T extends NativeType<T>> DataSource<D, T>
	openAsLabelSource(
			final N5Reader reader,
			final String dataset,
			final AffineTransform3D transform,
			final AxisOrder axisOrder,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws IOException, ReflectionException, AxisOrderNotSupported {
		return N5Types.isLabelMultisetType(reader, dataset)
		       ? (DataSource<D, T>) openLabelMultisetAsSource(reader, dataset, transform, axisOrder, globalCache, priority, name)
		       : (DataSource<D, T>) openScalarAsSource(reader, dataset, transform, axisOrder, globalCache, priority, name);
	}

	/**
	 * Adjust {@link AffineTransform3D} by scaling and translating appropriately.
	 * @param transform to be adjusted wrt to downsampling factors
	 * @param downsamplingFactors at target level
	 * @param initialDownsamplingFactors at source level
	 * @return adjusted {@link AffineTransform3D}
	 */
	public static AffineTransform3D considerDownsampling(
			final AffineTransform3D transform,
			final double[] downsamplingFactors,
			final double[] initialDownsamplingFactors)
	{
		final double[] shift = new double[downsamplingFactors.length];
		for (int d = 0; d < downsamplingFactors.length; ++d)
		{
			transform.set(transform.get(d, d) * downsamplingFactors[d] / initialDownsamplingFactors[d], d, d);
			shift[d] = 0.5 / initialDownsamplingFactors[d] - 0.5 / downsamplingFactors[d];
		}
		return transform.concatenate(new Translation3D(shift));
	}

	/**
	 * Get appropriate {@link FragmentSegmentAssignmentState} for {@code group} in n5 container {@code writer}
	 * @param writer container
	 * @param group group
	 * @return {@link FragmentSegmentAssignmentState}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static FragmentSegmentAssignmentState assignments(final N5Writer writer, final String group)
	throws IOException
	{

		if (!isPainteraDataset(writer, group))
		{
			return new FragmentSegmentAssignmentOnlyLocal(
					TLongLongHashMap::new,
					(ks, vs) -> {
						throw new UnableToPersist("Persisting assignments not supported for non Paintera " +
								"group/dataset" +
								" " + group);
					}
			);
		}

		final String dataset = group + "/" + PAINTERA_FRAGMENT_SEGMENT_ASSIGNMENT_DATASTE;

		final Persister persister = (keys, values) -> {
			// TODO how to handle zero length assignments?
			if (keys.length == 0)
			{
				throw new UnableToPersist("Zero length data, will not persist fragment-segment-assignment.");
			}
			try
			{
				final DatasetAttributes attrs = new DatasetAttributes(
						new long[] {keys.length, 2},
						new int[] {keys.length, 1},
						DataType.UINT64,
						new GzipCompression()
				);
				writer.createDataset(dataset, attrs);
				final DataBlock<long[]> keyBlock = new LongArrayDataBlock(
						new int[] {keys.length, 1},
						new long[] {0, 0},
						keys
				);
				final DataBlock<long[]> valueBlock = new LongArrayDataBlock(
						new int[] {values.length, 1},
						new long[] {0, 1},
						values
				);
				writer.writeBlock(dataset, attrs, keyBlock);
				writer.writeBlock(dataset, attrs, valueBlock);
			} catch (final Exception e)
			{
				throw new UnableToPersist(e);
			}
		};

		final Supplier<TLongLongMap> initialLutSupplier = MakeUnchecked.supplier(() -> {
			final long[] keys;
			final long[] values;
			LOG.debug("Found fragment segment assingment dataset {}? {}", dataset, writer.datasetExists(dataset));
			if (writer.datasetExists(dataset))
			{
				final DatasetAttributes attrs      = writer.getDatasetAttributes(dataset);
				final int               numEntries = (int) attrs.getDimensions()[0];
				keys = new long[numEntries];
				values = new long[numEntries];
				LOG.debug("Found {} assignments", numEntries);
				final RandomAccessibleInterval<UnsignedLongType> data = N5Utils.open(writer, dataset);

				final Cursor<UnsignedLongType> keysCursor = Views.flatIterable(Views.hyperSlice(data, 1, 0L)).cursor();
				for (int i = 0; keysCursor.hasNext(); ++i)
				{
					keys[i] = keysCursor.next().get();
				}

				final Cursor<UnsignedLongType> valuesCursor = Views.flatIterable(Views.hyperSlice(
						data,
						1,
						1L
				                                                                                 )).cursor();
				for (int i = 0; valuesCursor.hasNext(); ++i)
				{
					values[i] = valuesCursor.next().get();
				}
			}
			else
			{
				keys = new long[] {};
				values = new long[] {};
			}
			return new TLongLongHashMap(keys, values);
		});

		return new FragmentSegmentAssignmentOnlyLocal(initialLutSupplier, persister);
	}

	/**
	 * Get id-service for n5 {@code container} and {@code dataset}.
	 * Requires write access on the attributes of {@code dataset} and attribute {@code "maxId": <maxId>} in {@code dataset}.
	 * @param n5 container
	 * @param dataset dataset
	 * @return {@link N5IdService}
	 * @throws IOException If no attribute {@code "maxId": <maxId>} in {@code dataset} or any n5 operation throws.
	 */
	public static IdService idService(final N5Writer n5, final String dataset) throws IOException
	{

		LOG.debug("Requesting id service for {}:{}", n5, dataset);
		final Long maxId = n5.getAttribute(dataset, "maxId", Long.class);
		LOG.debug("Found maxId={}", maxId);
		if (maxId == null) { throw new IOException("maxId not specified in attributes.json"); }
		return new N5IdService(n5, dataset, maxId);

	}

	/**
	 *
	 * @param n5 container
	 * @param group scale group
	 * @return {@code "group/s0"} if {@code "s0" is the finest scale level}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static String getFinestLevel(
			final N5Reader n5,
			final String group) throws IOException
	{
		LOG.debug("Getting finest level for dataset {}", group);
		final String[] scaleDirs = listAndSortScaleDatasets(n5, group);
		return Paths.get(group, scaleDirs[0]).toString();
	}

	/**
	 *
	 * @param n5 container
	 * @param group scale group
	 * @return {@code "group/sN"} if {@code "sN" is the coarsest scale level}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static String getCoarsestLevel(
			final N5Reader n5,
			final String group) throws IOException
	{
		final String[] scaleDirs = listAndSortScaleDatasets(n5, group);
		return Paths.get(group, scaleDirs[scaleDirs.length - 1]).toString();
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param key key for array attribute
	 * @param fallBack if key not present, return this value instead
	 * @return value of attribute at {@code key} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getDoubleArrayAttribute(
			final N5Reader n5,
			final String group,
			final String key,
			final double... fallBack) throws IOException
	{
		return getDoubleArrayAttribute(n5, group, key, false, fallBack);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param key key for array attribute
	 * @param revert set to {@code true} to revert order of array entries
	 * @param fallBack if key not present, return this value instead
	 * @return value of attribute at {@code key} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getDoubleArrayAttribute(
			final N5Reader n5,
			final String group,
			final String key,
			final boolean revert,
			final double... fallBack)
	throws IOException
	{

		if (revert)
		{
			final double[] toRevert = getDoubleArrayAttribute(n5, group, key, false, fallBack);
			LOG.debug("Will revert {}", toRevert);
			for ( int i = 0, k = toRevert.length - 1; i < toRevert.length / 2; ++i, --k)
			{
				double tmp = toRevert[i];
				toRevert[i] = toRevert[k];
				toRevert[k] = tmp;
			}
			LOG.debug("Reverted {}", toRevert);
			return toRevert;
		}

		if (isPainteraDataset(n5, group))
		{
			//noinspection ConstantConditions
			return getDoubleArrayAttribute(n5, group + "/" + PAINTERA_DATA_DATASET, key, revert, fallBack);
		}
		try {
			return Optional.ofNullable(n5.getAttribute(group, key, double[].class)).orElse(fallBack);
		}
		catch (ClassCastException e)
		{
			LOG.debug("Caught exception when trying to read double[] attribute. Will try to read as long[] attribute instead.", e);
			return Optional.ofNullable(asDoubleArray(n5.getAttribute(group, key, long[].class))).orElse(fallBack);
		}
	}


	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @return value of attribute at {@code "resolution"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getResolution(final N5Reader n5, final String group) throws IOException
	{
		return getResolution(n5, group, false);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param revert set to {@code true} to revert order of array entries
	 * @return value of attribute at {@code "resolution"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getResolution(final N5Reader n5, final String group, boolean revert) throws IOException
	{
		return getDoubleArrayAttribute(n5, group, RESOLUTION_KEY, revert, 1.0, 1.0, 1.0);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @return value of attribute at {@code "offset"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getOffset(final N5Reader n5, final String group) throws IOException
	{
		return getOffset(n5, group, false);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param revert set to {@code true} to revert order of array entries
	 * @return value of attribute at {@code "offset"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getOffset(final N5Reader n5, final String group, boolean revert) throws IOException
	{
		return getDoubleArrayAttribute(n5, group, OFFSET_KEY, revert,0.0, 0.0, 0.0);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @return value of attribute at {@code "downsamplingFactors"} as {@code double[]}
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static double[] getDownsamplingFactors(final N5Reader n5, final String group) throws IOException
	{
		return getDoubleArrayAttribute(n5, group, DOWNSAMPLING_FACTORS_KEY, 1.0, 1.0, 1.0);
	}

	/**
	 *
	 * @param resolution voxel-size
	 * @param offset in real-world coordinates
	 * @return {@link AffineTransform3D} with {@code resolution} on diagonal and {@code offset} on 4th column.
	 */
	public static AffineTransform3D fromResolutionAndOffset(final double[] resolution, final double[] offset)
	{
		return new AffineTransform3D().concatenate(new ScaleAndTranslation(resolution, offset));
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @return {@link AffineTransform3D} that transforms voxel space to real world coordinate space.
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static AffineTransform3D getTransform(final N5Reader n5, final String group) throws IOException
	{
		return getTransform(n5, group, false);
	}

	/**
	 *
	 * @param n5 container
	 * @param group group
	 * @param revertSpatialAttributes revert offset and resolution attributes if {@code true}
	 * @return {@link AffineTransform3D} that transforms voxel space to real world coordinate space.
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static AffineTransform3D getTransform(final N5Reader n5, final String group, boolean revertSpatialAttributes) throws IOException
	{
		return fromResolutionAndOffset(getResolution(n5, group, revertSpatialAttributes), getOffset(n5, group, revertSpatialAttributes));
	}

	/**
	 * Return the last segment of a path to a group/dataset in n5 container (absolute or relative)
	 * @param group /some/path/to/group
	 * @return last segment of {@code group}: {@code "group"}
	 */
	public static String lastSegmentOfDatasetPath(final String group)
	{
		return Paths.get(group).getFileName().toString();
	}

	/**
	 *
	 * @param reader container
	 * @param group needs to be paitnera dataset to return meaningful lookup
	 * @return unsupported lookup if {@code is not a paintera dataset}, {@link LabelBlockLookup} otherwise.
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static LabelBlockLookup getLabelBlockLookup(N5Reader reader, String group) throws IOException
	{
		// TODO fix this, we don't always want to return file-based lookup!!!
		try {
			LOG.debug("Getting label block lookup for {}", N5Meta.fromReader(reader, group));
			if (reader instanceof N5FSReader && isPainteraDataset(reader, group)) {
				N5FSMeta n5fs = new N5FSMeta((N5FSReader) reader, group);
				final GsonBuilder gsonBuilder = new GsonBuilder().registerTypeHierarchyAdapter(LabelBlockLookup.class, LabelBlockLookupAdapter.getJsonAdapter());
				final Gson gson = gsonBuilder.create();
				final JsonElement labelBlockLookupJson = reader.getAttribute(group, "labelBlockLookup", JsonElement.class);
				LOG.debug("Got label block lookup json: {}", labelBlockLookupJson);
				final LabelBlockLookup lookup = Optional
						.ofNullable(labelBlockLookupJson)
						.filter(JsonElement::isJsonObject)
						.map(obj -> gson.fromJson(obj, LabelBlockLookup.class))
						.orElseGet(MakeUnchecked.supplier(() -> new LabelBlockLookupFromFile(Paths.get(n5fs.basePath(), group, "/", "label-to-block-mapping", "s%d", "%d").toString())));
				LOG.debug("Got lookup type: {}", lookup.getClass());
				return lookup;
			} else
				return new LabelBlockLookupNotSupportedForNonPainteraDataset();
		}
		catch (final ReflectionException e)
		{
			throw new IOException(e);
		}
	}

	@LabelBlockLookup.LookupType("UNSUPPORTED")
	private static class LabelBlockLookupNotSupportedForNonPainteraDataset implements LabelBlockLookup
	{

		private LabelBlockLookupNotSupportedForNonPainteraDataset()
		{
			LOG.info("3D meshes not supported for non Paintera dataset!");
		}

		@NotNull
		@Override
		public Interval[] read( int level, long id )
		{
			LOG.debug("Reading blocks not supported for non-paintera dataset -- returning empty array");
			return new Interval[ 0 ];
		}

		@Override
		public void write( int level, long id, Interval... intervals )
		{
			LOG.debug("Saving blocks not supported for non-paintera dataset");
		}

		// This is here because annotation interfaces cannot have members in kotlin (currently)
		// https://stackoverflow.com/questions/49661899/java-annotation-implementation-to-kotlin
		@NotNull
		@Override
		public String getType()
		{
			return "UNSUPPORTED";
		}
	}

	/**
	 *
	 * @param container container
	 * @param group target group in {@code container}
	 * @param dimensions size
	 * @param blockSize chunk size
	 * @param resolution voxel size
	 * @param offset in world coordinates
	 * @param relativeScaleFactors relative scale factors for multi-scale data, e.g.
	 * {@code [2,2,1], [2,2,2]} will result in absolute factors {@code [1,1,1], [2,2,1], [4,4,2]}.
	 * @param maxNumEntries limit number of entries in each {@link LabelMultiset} (set to less than or equal to zero for unbounded)
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static void createEmptyLabeLDataset(
			String container,
			String group,
			long[] dimensions,
			int[] blockSize,
			double[] resolution,
			double[] offset,
			double[][] relativeScaleFactors,
			int[] maxNumEntries) throws IOException
	{
		createEmptyLabeLDataset(container, group, dimensions, blockSize, resolution, offset,relativeScaleFactors, maxNumEntries, false);
	}

	/**
	 *
	 * @param container container
	 * @param group target group in {@code container}
	 * @param dimensions size
	 * @param blockSize chunk size
	 * @param resolution voxel size
	 * @param offset in world coordinates
	 * @param relativeScaleFactors relative scale factors for multi-scale data, e.g.
	 * {@code [2,2,1], [2,2,2]} will result in absolute factors {@code [1,1,1], [2,2,1], [4,4,2]}.
	 * @param maxNumEntries limit number of entries in each {@link LabelMultiset} (set to less than or equal to zero for unbounded)
	 * @param ignoreExisiting overwrite any existing data set
	 * @throws IOException if any n5 operation throws {@link IOException} or {@code group}
	 * already exists and {@code ignorExisting} is {@code false}
	 */
	public static void createEmptyLabeLDataset(
			String container,
			String group,
			long[] dimensions,
			int[] blockSize,
			double[] resolution,
			double[] offset,
			double[][] relativeScaleFactors,
			int[] maxNumEntries,
			boolean ignoreExisiting) throws IOException
	{

		//		{"painteraData":{"type":"label"},
		// "maxId":191985,
		// "labelBlockLookup":{"attributes":{},"root":"/home/phil/local/tmp/sample_a_padded_20160501.n5",
		// "scaleDatasetPattern":"volumes/labels/neuron_ids/oke-test/s%d","type":"n5-filesystem"}}

		final Map<String, String> pd = new HashMap<>();
		pd.put("type", "label");
		final N5FSWriter n5 = new N5FSWriter(container);
		final String uniqueLabelsGroup = String.format("%s/unique-labels", group);

		if (!ignoreExisiting && n5.datasetExists(group))
			throw new IOException(String.format("Dataset `%s' already exists in container `%s'", group, container));

		if (!n5.exists(group))
			n5.createGroup(group);

		if (!ignoreExisiting && n5.listAttributes(group).containsKey(PAINTERA_DATA_KEY))
			throw new IOException(String.format("Group `%s' exists in container `%s' and is Paintera data set", group, container));

		if (!ignoreExisiting && n5.exists(uniqueLabelsGroup))
			throw new IOException(String.format("Unique labels group `%s' exists in container `%s' -- conflict likely.", uniqueLabelsGroup, container));

		n5.setAttribute(group, PAINTERA_DATA_KEY, pd);
		n5.setAttribute(group, MAX_ID_KEY, 1L);

		final String dataGroup = String.format("%s/data", group);
		n5.createGroup(dataGroup);

		// {"maxId":191978,"multiScale":true,"offset":[3644.0,3644.0,1520.0],"resolution":[4.0,4.0,40.0],
		// "isLabelMultiset":true}%
		n5.setAttribute(dataGroup, MULTI_SCALE_KEY, true);
		n5.setAttribute(dataGroup, OFFSET_KEY, offset);
		n5.setAttribute(dataGroup, RESOLUTION_KEY, resolution);
		n5.setAttribute(dataGroup, IS_LABEL_MULTISET_KEY, true);

		n5.createGroup(uniqueLabelsGroup);
		n5.setAttribute(uniqueLabelsGroup, MULTI_SCALE_KEY, true);

		final String scaleDatasetPattern      = String.format("%s/s%%d", dataGroup);
		final String scaleUniqueLabelsPattern = String.format("%s/s%%d", uniqueLabelsGroup);
		final long[]       scaledDimensions         = dimensions.clone();
		final double[] accumulatedFactors = new double[] {1.0, 1.0, 1.0};
		for (int scaleLevel = 0, downscaledLevel = -1; downscaledLevel < relativeScaleFactors.length; ++scaleLevel, ++downscaledLevel)
		{
			double[]     scaleFactors       = downscaledLevel < 0 ? null : relativeScaleFactors[downscaledLevel];

			final String dataset            = String.format(scaleDatasetPattern, scaleLevel);
			final String uniqeLabelsDataset = String.format(scaleUniqueLabelsPattern, scaleLevel);
			final int maxNum = downscaledLevel < 0 ? -1 : maxNumEntries[downscaledLevel];
			n5.createDataset(dataset, scaledDimensions, blockSize, DataType.UINT8, new GzipCompression());
			n5.createDataset(uniqeLabelsDataset, scaledDimensions, blockSize, DataType.UINT64, new GzipCompression());

			// {"maxNumEntries":-1,"compression":{"type":"gzip","level":-1},"downsamplingFactors":[2.0,2.0,1.0],"blockSize":[64,64,64],"dataType":"uint8","dimensions":[625,625,125]}%
			n5.setAttribute(dataset, MAX_NUM_ENTRIES_KEY, maxNum);
			if (scaleLevel == 0)
				n5.setAttribute(dataset, IS_LABEL_MULTISET_KEY, true);
			else
			{
				n5.setAttribute(dataset, DOWNSAMPLING_FACTORS_KEY, accumulatedFactors);
				n5.setAttribute(uniqeLabelsDataset, DOWNSAMPLING_FACTORS_KEY, accumulatedFactors);
			}



			// {"compression":{"type":"gzip","level":-1},"downsamplingFactors":[2.0,2.0,1.0],"blockSize":[64,64,64],"dataType":"uint64","dimensions":[625,625,125]}

			if (scaleFactors != null)
			{
				Arrays.setAll(scaledDimensions, dim -> (long) Math.ceil(scaledDimensions[dim] / scaleFactors[dim]));
				Arrays.setAll(accumulatedFactors, dim -> accumulatedFactors[dim] * scaleFactors[dim]);
			}
		}
	}

	/**
	 *
	 * @param reader container
	 * @param dataset dataset
	 * @return {@link CellGrid} that is equivalent to dimensions and block size of dataset
	 * @throws IOException if any n5 operation throws {@link IOException}
	 */
	public static CellGrid getGrid(N5Reader reader, final String dataset) throws IOException {
		return asCellGrid(reader.getDatasetAttributes(dataset));
	}


	/**
	 *
	 * @param attributes attributes
	 * @return {@link CellGrid} that is equivalent to dimensions and block size of {@code attributes}
	 */
	public static CellGrid asCellGrid(DatasetAttributes attributes)
	{
		return new CellGrid(attributes.getDimensions(), attributes.getBlockSize());
	}

	/**
	 *
	 * @param group dataset, multi-scale group, or paintera dataset
	 * @param isPainteraDataset set to {@code true} if {@code group} is paintera data set
	 * @return multi-scale group or n5 dataset
	 */
	public static String volumetricDataGroup(final String group, final boolean isPainteraDataset)
	{
		return isPainteraDataset
				? group + "/" + N5Helpers.PAINTERA_DATA_DATASET
				: group;
	}

	/**
	 *
	 * @param n5 container
	 * @param group  multi-scale group
	 * @return {@code group} if {@code group} is n5 dataset, {@code group/s0} if {@code group} is multi-scale
	 */
	public static String highestResolutionDataset(final N5Reader n5, final String group) throws IOException {
		return highestResolutionDataset(n5, group, N5Helpers.isMultiScale(n5, group));
	}


	/**
	 *
	 * @param n5 container
	 * @param group  multi-scale group
	 * @param isMultiscale set to true if {@code group} is multi-scale
	 * @return {@code group} if {@code group} is n5 dataset, {@code group/s0} if {@code isMultiscale} is {@code true}
	 */
	public static String highestResolutionDataset(final N5Reader n5, final String group, final boolean isMultiscale) throws IOException {
		return isMultiscale
				? Paths.get(group, N5Helpers.listAndSortScaleDatasets(n5, group)[0]).toString()
				: group;
	}

	private static double[] asDoubleArray(long[] array)
	{
		final double[] doubleArray = new double[array.length];
		Arrays.setAll(doubleArray, d -> array[d]);
		return doubleArray;
	}
}
