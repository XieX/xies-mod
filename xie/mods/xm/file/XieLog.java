package xie.mods.xm.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import xie.mods.xm.util.ItemWrapper;
import xie.mods.xm.util.Xie;

public class XieLog {
	
	private Logger log;
	private static XieLog instance;
	
	public void logFailure(String s){log.log(XieLevel.FAILURE,s); }
	public void logIncomplete(String s) { log.log(XieLevel.INCOMPLETE,s); }
	public void logDelayed(String s) { log.log(XieLevel.DELAYED, s); }
	public void info(String s) { log.info(s); }
	public void config(String s) { log.config(s); }
	public void fine(String s) { log.fine(s); }
	public void finer(String s) { log.finer(s); }
	
	public static XieLog log() {
		if (instance==null) instance = new XieLog();
		return instance;
	}
	
	private XieLog() {
		log = Logger.getAnonymousLogger();
		log.setUseParentHandlers(false);
		log.setLevel(Level.FINER);
		FileHandler handler;
		try {
			handler = new FileHandler(PathManager.get().htmlLogFile().getAbsolutePath());
			handler.setFormatter(new XieFormatter());
			log.addHandler(handler);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void readStyleFromFile(Reader reader) {
		String str = "";
		try {
			BufferedReader r = new BufferedReader(reader);
		
			String line = r.readLine();
			do {
				str+=line+"\n";
				line = r.readLine();
			} while (line!=null);
			
			r.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (str!=null && !str.isEmpty()) XieFormatter.cssStyle = str;
	}

	public void importTranslations(Reader r) {
		Properties props = new Properties();
		
		try {
			props.load(r);
		} catch (FileNotFoundException e) {
			logIncomplete("Could not read translation table, FileNotFoundException");
			return;
		} catch (IOException e) {
			logIncomplete("Could not read translation table, IOException");
			return;
		}
		
		for (Object key : props.keySet()) {
			String s = (String)key;
			ItemWrapper iw = Xie.parseImplicitItemWrapper(s);
			if (iw==null || !iw.exists()) {
				logIncomplete("Failed to parse translation table entry "+s);
				continue;
			}
			String values = props.getProperty(s);
			if (values==null) {
				logIncomplete("Error with translation table values for entry "+s);
				continue;
			}
			String names[] = values.trim().toLowerCase().split(",");
			if (names.length<=0) {
				logIncomplete("Error with translation table values for entry "+s);
				continue;
			}
			for (String n : names) Xie.translationMap.put(n.toLowerCase(),iw);
		}
	}
	
	public void closeLogger() {
		Handler handlers[] = log.getHandlers();
		for (Handler h : handlers) h.close();
	}
}
