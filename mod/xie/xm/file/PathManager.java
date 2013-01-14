package mod.xie.xm.file;

import java.io.File;
import java.io.IOException;


import mod.xie.xm.XiesMod;
import net.minecraft.src.ModLoader;


public class PathManager {
	private File MC_PATH;
	private final String CONFIG_DIR = "/config/Xie";
	private final String CONTENT_DIR = "/mods/Xie";
	private final String MODS_DIR = "/mods";
	private final String CACHE_DIR = "/_cache";
	private final String LOG_FILE = "/XieLog.txt";
	private final String HTML_LOG = "/XieLog.html";

	private static PathManager instance;
	
	private File cacheDir;
	private File configDir;
	private File contentDir;
	private File htmlLogFile;
	private File txtLogFile;
	private File modsDir;
	
	public static PathManager get() {
		if (instance==null) instance = new PathManager();
		return instance;
	}
	
	private PathManager() {
		MC_PATH = getMinecraftDir();
		cacheDir = newDir(CONTENT_DIR + CACHE_DIR);
		configDir = newDir(CONFIG_DIR);
		modsDir = newDir(MODS_DIR);
		contentDir = newDir(CONTENT_DIR);
		htmlLogFile = newFile(HTML_LOG);
		txtLogFile = newFile(LOG_FILE);
	}
	
	private File newDir(String path) {
		File dir = new File(MC_PATH, path);
		if (!dir.exists()) dir.mkdirs();
		return dir;
	}
	
	private File newFile(String path) {
		File file = new File(MC_PATH, path);
		if (!file.exists()) {
			try {
				File dir = file.getParentFile();
				if (dir!=null && !dir.exists()) dir.mkdirs();
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return file;
	}
	
	public static String getMinecraftPath() {
		return cleanPath(XiesMod.proxy.getMinecraftPath());
	}
	
	public static File getMinecraftDir() {
		return new File(getMinecraftPath());
	}
	
	public static String cleanPath(String s) {
		if (File.separatorChar!='/') s=s.replace('/', File.separatorChar);
		if (File.separatorChar!='\\') s=s.replace('\\', File.separatorChar);
		return s;
	}

	public File cacheDir() {
		return cacheDir;
	}

	public File configDir() {
		return configDir;
	}
	
	public File modsDir() {
		return modsDir;
	}

	public File contentDir() {
		return contentDir;
	}

	public File htmlLogFile() {
		return htmlLogFile;
	}

	public File textLogFile() {
		return txtLogFile;
	}

	public String localContentPath() {
		return CONTENT_DIR + "/";
	}

	public String localiseToCache(String f) {
		if (f.contains(CACHE_DIR)) {
			return f.substring(f.indexOf(CACHE_DIR)+CACHE_DIR.length());
		} else {
			System.err.println("XIE: Expecing cache directory in path "+f+", but didn't find it");
			return f;
		}
	}
	
	public String localiseToContent(String f) {
		if (f.contains(CONTENT_DIR)) {
			return f.substring(f.indexOf(CONTENT_DIR)+CONTENT_DIR.length());
		} else {
			System.err.println("XIE: Expecing content directory in path "+f+", but didn't find it");
			return f;
		}
	}
}
