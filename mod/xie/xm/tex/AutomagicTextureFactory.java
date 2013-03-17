package mod.xie.xm.tex;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.Icon;

public class AutomagicTextureFactory {

	private final static Pattern regexComplex = Pattern.compile("\\A[^_]+_{1}\\D+_{1}\\d+\\.png\\z",Pattern.CASE_INSENSITIVE);
	private final static Pattern regexMeta = Pattern.compile("\\A[^_]+_{1}\\d+\\.png\\z",Pattern.CASE_INSENSITIVE);
	private final static Pattern regexSide = Pattern.compile("\\A[^_]+_{1}\\D+\\.png\\z",Pattern.CASE_INSENSITIVE);

	public AutomagicTextureFactory() {
		
	}
	
	private static AutomagicTextureFactory instance;
	
	public static AutomagicTextureFactory getInstance() {
		if (instance==null) instance = new AutomagicTextureFactory();
		return instance;
	}
	
	public static XieTex get(IconRegister register, String name, String path) {
		return getInstance().makeXieTex(register, name, path);
	}
	
	public XieTex makeXieTex(IconRegister register, String name, String path) {
		String [] files = getTextureFileNames(name, path);
		
		if (files != null) {
		
			// If there is only one texture file, assume it's simple
			if (files.length == 1) {
				return new XieTexSimple(register, files[0]);
			}
			
			// Sort found texture files into categories
			String simple = null; // there can be only one
			LinkedList<String> meta = new LinkedList<String>();
			LinkedList<String> sided = new LinkedList<String>();
			
			for (String file : files) {
				if (regexComplex.matcher(file).matches()) {
					// if just one complex texture file is present, let the complex constructor deal with it
					return new XieTexComplex(register, files);
				} else if (regexMeta.matcher(file).matches()) {
					// if a mix of meta and sided filenames is detected, again let the complex constructor deal with it
					if (!sided.isEmpty()) return new XieTexComplex(register, files);
					meta.add(file);
				} else if (regexSide.matcher(file).matches()) {
					// if a mix of meta and sided filenames is detected, again let the complex constructor deal with it
					if (!meta.isEmpty()) return new XieTexComplex(register, files);
					sided.add(file);
				} else {
					simple = file;
				}
			}
			
			if (!meta.isEmpty()) {
				if (simple == null) return new XieTexMetaVariable(register, meta.toArray(new String[0]));
				else return new XieTexMetaVariable(register, meta.toArray(new String[0]), simple);
			}
			
			if (!sided.isEmpty()) {
				if (simple == null) return new XieTexSideVariable(register, sided.toArray(new String[0]));
				else return new XieTexSideVariable(register, sided.toArray(new String[0]), simple);
			}
		
		}
		return XieFailTex.getInstance();
	}
	
	private String [] getTextureFileNames(String name, String path) {
		String[] filenames = new File(path).list(new TextureFileFilter(name));
		
		if (filenames == null) {
			// TODO throw an error (not a directory or IO error)
		} else if (filenames.length == 0) {
			// TODO throw an error (directory empty or no matching files)
		} else return filenames;
		
		return null;
	}

}
