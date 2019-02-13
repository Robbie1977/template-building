package evaluation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.janelia.utility.parse.ParseUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import sc.fiji.skeleton.SwcIO;
import sc.fiji.skeleton.SWCPoint;


public class SwcProcess
{

	@Parameter(names = {"-s"}, description = "Input skeletons. You can provide multiple -s options" )
	private List<String> skeletonPaths;

	@Parameter(names = {"-o"}, description = "Output skeletons. You can  " )
	private List<String> outputSkeletonPaths;

	@Parameter(names = {"-c"}, description = "Coordinate scaling",
			converter = ParseUtils.DoubleArrayConverter.class )
	private double[] coordinateScaling;

	@Parameter(names = {"-r"}, description = "Radius scaling" )
	private double radiusScaling = 1.0;

	@Parameter(names = {"--set-radius"}, description = "Set radius. "
			+ "The radius at every point will be set to this value. "
			+ "This takes precedence over radius scaling." )
	private double radiusValue = Double.NaN;

	@Parameter(names = {"-h", "--help"}, description = "Print this help message" )
	private boolean help;
	
	private transient JCommander jCommander;
	

	/*
	 * Reads the first argument as a swc file
	 * If the second argument is a directory, will rename the input and write to that directory,
	 * else, will treat the second argument as the path of the output.
	 */
	public static void main( String[] args )
	{
		SwcProcess transformer = parseCommandLineArgs( args );
		
		if( args.length == 0 || transformer.help )
		{
			transformer.jCommander.usage();
			return;
		}

		transformer.run();
	}

	public static SwcProcess parseCommandLineArgs( final String[] args )
	{
		SwcProcess ob = new SwcProcess();
		ob.initCommander();
		try 
		{
			ob.jCommander.parse( args );
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		return ob;
	}

	private void initCommander()
	{
		jCommander = new JCommander( this );
		jCommander.setProgramName( "input parser" ); 
	}

	public void run()
	{
		
		if( skeletonPaths.size() != outputSkeletonPaths.size() )
		{
			System.err.println("Must have the same number of input and output skeleton arguments");
			return;
		}
		
		int i = 0;
		while( i < skeletonPaths.size())
		{
			final File in = new File( skeletonPaths.get( i ));
			final File out = new File( outputSkeletonPaths.get( i ));
			
			ArrayList< SWCPoint > current = SwcIO.loadSWC( in );

			ArrayList< SWCPoint > res = current; 
			if ( coordinateScaling != null )
				current = scale( current, coordinateScaling );

			if( Double.isNaN( radiusValue ))
			{
				if ( radiusScaling != 1.0 )
					res = scaleRadius( current, radiusScaling );
				else
					res = current;
			}
			else
			{
				res = setRadius( current, radiusValue );
			}

			System.out.println( "Exporting to " + out );
			try
			{
				final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream( out ), "UTF-8"));
				TransformSwc.flushSWCPoints( res, pw);
			}
			catch (final IOException ioe)
			{
				System.err.println("Saving to " + out + " failed");
			}

			i++;
		}
	}

	public static ArrayList<SWCPoint> setRadius( ArrayList<SWCPoint> pts, double value )
	{
		ArrayList<SWCPoint> out = new ArrayList<SWCPoint>();
		pts.forEach( pt -> out.add( setRadius( pt, value )) );
		return out;
	}

	public static SWCPoint setRadius( SWCPoint pt, double value )
	{
		SwcIO.StringPrintWriter spw = new SwcIO.StringPrintWriter();
		//System.out.println( pt.toString() );
		pt.println( spw );

		String[] s = spw.toString().split( " " );
		int id = Integer.parseInt( s[ 0 ] );
		int type = Integer.parseInt( s[ 1 ] );
		int previous = Integer.parseInt( s[ 6 ] );

		double[] p = new double[] { 
				pt.getPointInImage().x,
				pt.getPointInImage().y,
				pt.getPointInImage().z };

		return new SWCPoint( 
				id, type, 
				p[0], p[1], p[2], 
				value, previous );
	}

	public static ArrayList<SWCPoint> scaleRadius( ArrayList<SWCPoint> pts, double scale )
	{
		ArrayList<SWCPoint> out = new ArrayList<SWCPoint>();
		pts.forEach( pt -> out.add( scaleRadius( pt, scale )) );
		return out;
	}

	public static SWCPoint scaleRadius( SWCPoint pt, double scale )
	{
		SwcIO.StringPrintWriter spw = new SwcIO.StringPrintWriter();
		//System.out.println( pt.toString() );
		pt.println( spw );
		
		
		String[] s = spw.toString().split( " " );
		int id = Integer.parseInt( s[ 0 ] );
		int type = Integer.parseInt( s[ 1 ] );
		double radius = Double.parseDouble( s[ 5 ] );
		int previous = Integer.parseInt( s[ 6 ] );
		
		double[] p = new double[] { 
				pt.getPointInImage().x,
				pt.getPointInImage().y,
				pt.getPointInImage().z };

		return new SWCPoint( 
				id, type, 
				p[0], p[1], p[2], 
				scale * radius, previous );
	}

	public static ArrayList<SWCPoint> scale( ArrayList<SWCPoint> pts, double[] scale )
	{
		ArrayList<SWCPoint> out = new ArrayList<SWCPoint>();
		pts.forEach( pt -> out.add( scale( pt, scale )) );
		return out;
	}
	
	public static SWCPoint scale( SWCPoint pt, double[] scale )
	{
		SwcIO.StringPrintWriter spw = new SwcIO.StringPrintWriter();
		//System.out.println( pt.toString() );
		pt.println( spw );
		
		
		String[] s = spw.toString().split( " " );
		int id = Integer.parseInt( s[ 0 ] );
		int type = Integer.parseInt( s[ 1 ] );
		double radius = Double.parseDouble( s[ 5 ] );
		int previous = Integer.parseInt( s[ 6 ] );
		
		double[] p = new double[] { 
				scale[0] * pt.getPointInImage().x,
				scale[1] * pt.getPointInImage().y,
				scale[2] * pt.getPointInImage().z };

		return new SWCPoint( 
				id, type, 
				p[0], p[1], p[2], 
				radius, previous );
	}
	

}
