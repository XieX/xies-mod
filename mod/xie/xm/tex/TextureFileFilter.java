package mod.xie.xm.tex;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Pattern;

public class TextureFileFilter implements FilenameFilter {

	private Pattern regex;
	
	public TextureFileFilter(String name) {
		regex = Pattern.compile("\\A"+name+"(_.*)?\\.png\\z", Pattern.CASE_INSENSITIVE);
	}

	@Override
	public boolean accept(File dir, String filename) {
		return regex.matcher(filename).matches();
	}

}
