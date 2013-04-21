package mod.xie.xm;

import java.util.regex.Pattern;

// Keeping all of these in the one place so its easy to update them

public class Regexie {
	// awesomeblock_side_12.png
	public final static Pattern X_COMPLEX = Pattern.compile("\\A[^_]+_{1}\\D+_{1}\\d+\\.png\\z",Pattern.CASE_INSENSITIVE);
	// awesomeblock_12.png
	public final static Pattern X_META = Pattern.compile("\\A[^_]+_{1}\\d+\\.png\\z",Pattern.CASE_INSENSITIVE);
	// awesomeblock_side.png
	public final static Pattern X_SIDED = Pattern.compile("\\A[^_]+_{1}\\D+\\.png\\z",Pattern.CASE_INSENSITIVE);
	
	// awesomeblock(_side)*(_12)*.png
	public final static Pattern X_FILENAME = Pattern.compile("\\A[^_]+(_{1}\\D+)*(_{1}\\d+)*\\.png\\z",Pattern.CASE_INSENSITIVE);
	public static final short GROUP_SIDE = 1;
	public static final short GROUP_META = 2;
}
