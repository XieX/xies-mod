package xie.mods.xm.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class StyleFile {
	
	public static boolean WriteFile(File file) {
		try {
			if (!file.exists()) file.createNewFile();
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			writer.write(STYLE);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	
		return true;
	}
	
	private static final String STYLE = ""+
		"	.LEVELS {\n"+
		"		display: block;\n"+
		"		position: fixed;\n"+
		"		top:0;\n"+
		"		left:0;\n"+
		"		width:100%;\n"+
		"		height:24px;\n"+
		"		color: white;\n"+
		"		background-color: black;\n"+ 
		"		z-index:2;\n"+
		"	}\n"+
		"\n"+
		"	.LOG{\n"+
		"		display: block;\n"+
		"		position: relative;\n"+
		"		top:24px;\n"+
		"		left:0;\n"+
		"		width:100%;\n"+
		"		background-color: black;\n"+ 
		"		z-index:1;\n"+
		"	}\n"+
		"\n"+
		"	.FAILURE {\n"+
		"		display: block;\n"+
		"		color: #FF0000;\n"+
		"		background-color: black;\n"+
		"		width: 100%;\n"+
		"	}\n"+
		"\n"+
		"	.INCOMPLETE {\n"+
		"		display: block;\n"+
		"		color: #FF9900;\n"+
		"		background-color:\n"+
		"		black;\n"+
		"		width:100%;\n"+
		"	}\n"+
		"\n"+
		"	.DELAYED {\n"+ 
		"		display: none;\n"+
		"		color: #D0D0D0 ;\n"+
		"		background-color: black;\n"+ 
		"		width:100%;\n"+
		"	}\n"+
		"\n"+
		"	.INFO {\n"+ 
		"		display: block;\n"+
		"		color: white; \n"+
		"		background-color: black;\n"+ 
		"		width: 100%;\n"+
		"	}\n"+
		"\n"+
		"	.CONFIG {\n"+ 
		"		display: none;\n"+
		"		color: #C0C0C0 ; \n"+
		"		background-color: black;\n"+ 
		"		width: 100%;\n"+
		"	}\n"+
		"	\n"+
		"	.FINE {\n"+
		"		display: none;\n"+ 
		"		color: #A0A0A0 ; \n"+
		"		background-color: black;\n"+ 
		"		width:100%;\n"+
		"	}\n"+
		"	\n"+
		"	.FINER {\n"+
		"		display: none; \n"+
		"		color: #888888; \n"+
		"		background-color: black; \n"+
		"		width:100%;\n"+
		"	}";
}
