package io;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;

import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import sc.fiji.io.Dfield_Nrrd_Reader;

public class DfieldIoHelper
{

	public static final String MULT_KEY = "multiplier";

	public double[] spacing;
	
	public static void main( String[] args ) throws Exception
	{
		String dfieldIn = args[ 0 ];
		String dfieldOut = args[ 1 ];
		
		DfieldIoHelper io = new DfieldIoHelper();
		io.write( io.read( dfieldIn ), dfieldOut );
	}

	public < T extends RealType< T > & NativeType< T > > void write( final RandomAccessibleInterval< T > dfield, final String outputPath ) throws Exception
	{

		System.out.println( "dfield out sz: " + Util.printInterval( dfield ) );

		if ( outputPath.endsWith( "h5" ) )
		{
			System.out.println( "saving displacement field hdf5" );
			try
			{
				WriteH5DisplacementField.write( dfield, outputPath, new int[] { 3, 32, 32, 32 }, spacing, null );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if ( outputPath.endsWith( "nii" ) )
		{
			System.out.println( "saving displacement field nifti" );
			File outFile = new File( outputPath );
			Nifti_Writer writer = new Nifti_Writer( true );
			writer.save( ImageJFunctions.wrapFloat( dfield, "dfield" ), outFile.getParent(), outFile.getName() );
		}
		else if ( outputPath.endsWith( "nrrd" ) )
		{
			System.out.println( "saving displacement field nrrd" );

			File outFile = new File( outputPath );
			long[] subFactors = new long[] { 1, 1, 1, 1 };

			RandomAccessibleInterval< FloatType > raiF = Converters.convert( dfield, new Converter< T, FloatType >()
			{
				@Override
				public void convert( T input, FloatType output )
				{
					output.set( input.getRealFloat() );
				}
			}, new FloatType() );

			ImagePlus ip = ImageJFunctions.wrapFloat( raiF, "wrapped" );

			String nrrdHeader = WriteNrrdDisplacementField.makeDisplacementFieldHeader( ip, subFactors, "gzip" );
			if ( nrrdHeader == null )
			{
				System.err.println( "Failed" );
				return;
			}

			FileOutputStream out = new FileOutputStream( outFile );
			// First write out the full header
			Writer bw = new BufferedWriter( new OutputStreamWriter( out ) );

			// Blank line terminates header
			bw.write( nrrdHeader + "\n" );
			// Flush rather than close
			bw.flush();

			GZIPOutputStream dataStream = new GZIPOutputStream( new BufferedOutputStream( out ) );
			WriteNrrdDisplacementField.dumpFloatImg( raiF, null, false, dataStream );
		}
		else
		{
			System.out.println( "saving displacement other" );
			System.out.println( "size: " + Util.printInterval( dfield ));

			RandomAccessibleInterval< T > dfieldPerm = vectorAxisThird( dfield );
			System.out.println( "size perm: " + Util.printInterval( dfieldPerm ));

			ImagePlus dfieldip = ImageJFunctions.wrapFloat( dfieldPerm, "dfield" );

			IJ.save( dfieldip , outputPath );
		}
	}

	public < T extends RealType< T > > RandomAccessibleInterval< FloatType > read( final String fieldPath ) throws Exception
	{
		System.out.println("reading deformation field: " + fieldPath );

		ImagePlus dfieldIp = null;
		if ( fieldPath.endsWith( "nii" ) )
		{
			try
			{
				System.out.println( "loading nii" );
				dfieldIp = NiftiIo.readNifti( new File( fieldPath ) );

				spacing = new double[] { dfieldIp.getCalibration().pixelWidth, dfieldIp.getCalibration().pixelHeight, dfieldIp.getCalibration().pixelDepth };

			}
			catch ( FormatException e )
			{
				e.printStackTrace();
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}

		}
		else if ( fieldPath.endsWith( "nrrd" ) )
		{
			Dfield_Nrrd_Reader reader = new Dfield_Nrrd_Reader();
			File tmp = new File( fieldPath );
			dfieldIp = reader.load( tmp.getParent(), tmp.getName() );

			spacing = new double[]{ 
					dfieldIp.getCalibration().pixelWidth,
					dfieldIp.getCalibration().pixelHeight,
					dfieldIp.getCalibration().pixelDepth };

		}
		else if ( fieldPath.endsWith( "h5" ) )
		{
			try
			{
				N5HDF5Reader n5 = new N5HDF5Reader( fieldPath, 32, 32, 32, 3 );
				return N5DisplacementField.openField( n5, "dfield", new FloatType() );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			dfieldIp = IJ.openImage( fieldPath );
		}

		Img< FloatType > tmpImg = ImageJFunctions.wrapFloat( dfieldIp );
		return N5DisplacementField.vectorAxisLast( tmpImg );
	}

	public static final < T extends RealType< T > > RandomAccessibleInterval< T > vectorAxisThird( RandomAccessibleInterval< T > source ) throws Exception
	{
		final int n = source.numDimensions();
		int[] component = null;
		
		if( n != 4 )
		{
			throw new Exception( "Displacement field must be 4d" );
		}

		if ( source.dimension( 2 ) == 3 )
		{	
			return source;
		}
		else if ( source.dimension( 3 ) == 3 )
		{
			component = new int[ n ];
			component[ 0 ] = 0; 
			component[ 1 ] = 1; 
			component[ 2 ] = 3; 
			component[ 3 ] = 2;

			return N5DisplacementField.permute( source, component );
		}
		else if ( source.dimension( 0 ) == 3 )
		{
			component = new int[ n ];
			component[ 0 ] = 1;
			component[ 1 ] = 2;
			component[ 2 ] = 0;
			component[ 3 ] = 3;

			return N5DisplacementField.permute( source, component );
		}

		throw new Exception( 
				String.format( "Displacement fields must store vector components in the first or last dimension. " + 
						"Found a %d-d volume; expect size [%d,...] or [...,%d]", n, ( n - 1 ), ( n - 1 ) ) );
	}

}
