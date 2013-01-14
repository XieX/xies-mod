package mod.xie.xm.file;

import java.util.logging.Level;

public class XieLevel extends Level {

	public static XieLevel DELAYED = new XieLevel("DELAYED", 850);
	public static XieLevel INCOMPLETE = new XieLevel("INCOMPLETE", 920);
	public static XieLevel FAILURE = new XieLevel("FAILURE", 950);
	
	protected XieLevel(String name, int srsness) {
		super(name, srsness);
	}

}
