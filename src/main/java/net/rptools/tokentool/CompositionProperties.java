package net.rptools.tokentool;

public class CompositionProperties {

	private double translucency = 1;
	private int fudgeFactor = 20;
	private boolean solidBackground = false;
	private boolean base = false;
	
	public double getTranslucency() {
		return translucency;
	}
	public void setTranslucency(double alpha) {
		this.translucency = alpha;
	}
	public int getFudgeFactor() {
		return fudgeFactor;
	}
	public void setFudgeFactor(int fudgeFactor) {
		this.fudgeFactor = fudgeFactor;
	}
	public boolean isSolidBackground() {
		return solidBackground;
	}
	public boolean isBase() {
		return base;
	}
	public void setBase(boolean base) {
		this.base = base;
	}
	public void setSolidBackground(boolean solidBackground) {
		this.solidBackground = solidBackground;
	}
	
}
