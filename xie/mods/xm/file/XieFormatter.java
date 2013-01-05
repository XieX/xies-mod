package xie.mods.xm.file;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class XieFormatter extends Formatter {

	public static String cssStyle;
	public static String levels[] = new String[] {"FAILURE","INCOMPLETE","INFO","DELAYED","CONFIG","FINE","FINER"};
	public static boolean defaultChecked[] = new boolean[] {true,true,true,false,false,false,false};
	
	@Override
	public String format(LogRecord log) {
		StringBuffer s = new StringBuffer();

		s.append("<div class='"+log.getLevel().getName()+"'>");
		s.append(log.getMessage());
		s.append("</div>\n");
		
		return s.toString();
	}
	
	@Override
	public String getHead(Handler h) {
        return getHeader();
    }

    @Override
	public String getTail(Handler h) {
        return tail;
    }

    protected String getHeader() {
    	if (cssStyle==null) cssStyle=defaultStyle;
    	return "<html><head><title>"+title+"</title>\n"
        		+ "<style>\n" + cssStyle + "\n</style>" + js
        		+"</head><body onload='init()'>"+buildForm()+"\n"+bodyOpen+"\n";
    }
    
    protected String buildForm() {
    	String form = "<div class='LEVELS'><form name='lvl'>\n";
    	for (int i=0; i<levels.length; i++) {
    		String l = levels[i];
    		String checked = defaultChecked[i]?"checked":"";
    		form+="<input type='checkbox' id='"+l+"' value='"+l+"' "+checked+" onchange='update(this)' /> "+l+" | \n";
    	}
    	return form+"</form></div>";
    }
    
    protected static final String bodyOpen = "<div class='LOG'>";
    protected static final String bodyClose = "</div>";
    protected static final String title = "Xie Log";
    protected static final String js =
    		"<script type='text/javascript' src='http://www.shawnolson.net/scripts/public_smo_scripts.js'></script>"
    		+"<script type='text/javascript'>\n "
    		+"	// Cheers to Shawn Olson at http://www.shawnolson.net for the superuseful changecss function!\n"
    		+"	function update(box) {\n"
    		+"		if (box.checked) changecss('.'+box.value,'display','block');\n"
    		+"		else changecss('.'+box.value,'display','none');\n"
    		+"		document.cookie=box.value+'='+box.checked;"
    		+"	}</script>\n";
       
    protected static final String tail =
    		bodyClose+"\n</body></html>\n";
    protected static final String defaultStyle = ".LEVELS {display: block;position: fixed;top:0;left:0;width:100%;height:24px;color: white;background-color: black;z-index:2;}\n"
    		+".LOG{ display: block;position: relative;top:24px;left:0;width:100%;background-color: black; z-index:1;}\n"
    		+".FAILURE {display: block;color: red;background-color:black;width:100%;}\n"
    		+".INCOMPLETE {display: block;color: red;background-color:black;width:100%;}\n"
    		+".DELAYED {display: none;color: blue;background-color: black;	width:100%;}\n"
    		+".INFO {display: block;color: white;background-color: black;width: 100%;}\n"
    		+".CONFIG {display: none;color: blue;background-color: black;	width:100%;}\n"
    		+".FINE {display: none;color: grey;background-color: black;width:100%;}\n"
    		+".FINER {display: none;color: grey;background-color: black;width:100%;}\n";
}
