package mod.xie.xm.tex;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import java.util.regex.Pattern;

import mod.xie.xm.Regexie;
import mod.xie.xm.file.PathManager;
import mod.xie.xm.file.XieLog;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.item.Item;
import net.minecraft.src.ModLoader;
import net.minecraft.util.Icon;

public class AutomagicTextureFactory {

	private static final String XIE = "Xie";
	private static final String BLOCKS = "/textures/blocks";
	private static final String ITEMS = "/textures/items";
	
	public AutomagicTextureFactory() {
		
	}
	
	private static AutomagicTextureFactory instance;
	
	public static AutomagicTextureFactory getInstance() {
		if (instance==null) instance = new AutomagicTextureFactory();
		return instance;
	}
	
	public static XieTex getBlock(IconRegister register, String name) {
		return getBlock(register,name,XIE);
	}
	
	public static XieTex getBlock(IconRegister register, String name, String path) {
		return getInstance().makeXieTex(register, name, path+BLOCKS);
	}
	
	public static XieTex getItem(IconRegister register, String name) {
		return getItem(register,name,XIE);
	}
	
	public static XieTex getItem(IconRegister register, String name, String path) {
		return getInstance().makeXieTex(register, name, path+ITEMS);
	}
	
	public XieTex makeXieTex(IconRegister register, String name, String pathSuffix) {
		String path = PathManager.get().modsDir().toPath()+"/"+pathSuffix;
		String [] files = getTextureFileNames(name, path);
		
		if (files != null) {
		
			// If there is only one texture file, assume it's simple
			if (files.length == 1) {
				return new XieTexSimple(register, files[0]);
			}
			
			// Sort found texture files into categories
			String simple = null; // used as the default to fill gaps in non-simple texture objects
			LinkedList<String> meta = new LinkedList<String>(); // metavariant textures
			LinkedList<String> sided = new LinkedList<String>(); // side-variant textures
			
			for (String file : files) {
				if (Regexie.X_COMPLEX.matcher(file).matches()) {
					// even if just one complex texture file is present, let the complex constructor deal with it
					return new XieTexComplex(register, files);
				} else if (Regexie.X_META.matcher(file).matches()) {
					// if a mix of meta and sided filenames is detected, short-out and let the complex constructor deal with it instead
					if (!sided.isEmpty()) return new XieTexComplex(register, files);
					meta.add(file);
				} else if (Regexie.X_SIDED.matcher(file).matches()) {
					// if a mix of meta and sided filenames is detected, short-out and let the complex constructor deal with it instead
					if (!meta.isEmpty()) return new XieTexComplex(register, files);
					sided.add(file);
				} else {
					// to capture one (or the last of many) filenames that do not match a metvariant, side-variant or complex pattern
					// if files are incorrectly named (and aren't being correctly identified) they'll hit here and the last one will be
					// taken as the "simple" texture
					simple = file;
				}
			}
			
			if (!meta.isEmpty()) {
				if (simple == null) return new XieTexMetaVariable(register, meta.toArray(new String[0]));
				else return new XieTexMetaVariable(register, meta.toArray(new String[0]), simple);
			} else if (!sided.isEmpty()) {
				if (simple == null) return new XieTexSideVariable(register, sided.toArray(new String[0]));
				else return new XieTexSideVariable(register, sided.toArray(new String[0]), simple);
			} else if (simple != null) {
				// likely files were incorrectly named
				XieLog.log().logIncomplete("Texture file "+simple+" is being used as a simple texture for "+name+", but multiple texture files were found for block.");
				// TODO that's a terrible error message
			}
			XieLog.log().logFailure("Failed miserably to assign texture files for "+name+" from "+path);
		}
		return new XieFailTex(register);
	}
	
	private String [] getTextureFileNames(String name, String path) {
		File texDir = new File(PathManager.cleanPath(path));
		String[] filenames = texDir.list(new TextureFileFilter(name));
		
		if (filenames == null) {
			XieLog.log().logFailure("Error locating path "+path+" for block "+name);
		} else if (filenames.length == 0) {
			XieLog.log().logFailure("Found no matches for block "+name+" @ "+path);
		} else return filenames;
		
		return null;
	}

}
