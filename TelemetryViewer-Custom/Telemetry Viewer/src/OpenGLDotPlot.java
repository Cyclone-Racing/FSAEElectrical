import java.awt.Color;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

public class OpenGLDotPlot extends PositionedChart {

	Samples[] samples;

	int[][] bins; // [datasetN][binN]
	int binCount;

	// plot region
	float xPlotLeft;
	float xPlotRight;
	float plotWidth;
	float yPlotTop;
	float yPlotBottom;
	float plotHeight;

	// x-axis title
	boolean showXaxisTitle;
	float yXaxisTitleTextBasline;
	float yXaxisTitleTextTop;
	String xAxisTitle;
	float xXaxisTitleTextLeft;

	// legend
	boolean showLegend;
	float xLegendBorderLeft;
	float yLegendBorderBottom;
	float yLegendTextBaseline;
	float yLegendTextTop;
	float yLegendBorderTop;
	float[][] legendMouseoverCoordinates;
	float[][] legendBoxCoordinates;
	float[] xLegendNameLeft;
	float xLegendBorderRight;

	// x-axis scale
	boolean showXaxisScale;
	Map<Float, String> xDivisions;
	float yXaxisTickTextBaseline;
	float yXaxisTickTextTop;
	float yXaxisTickBottom;
	float yXaxisTickTop;

	// y-axis title
	boolean showYaxisTitle;
	float xYaxisTitleTextTop;
	float xYaxisTitleTextBaseline;
	String yAxisTitle;
	float yYaxisTitleText;

	// y-axis scale
	boolean showYaxisScale;
	Map<Float, String> yDivisions;
	float xYaxisTickTextBaseline;
	float xYaxisTickTextTop;
	float xYaxisTickBottom;
	float xYaxisTickTop;

	boolean yAxisShowsRelativeFrequency;
	boolean yAxisShowsFrequency;
	AutoScale yAutoscaleRelativeFrequency;
	AutoScale yAutoscaleFrequency;
	boolean yMinimumIsZero;
	boolean yAutoscaleMax;
	float manualMinY; // relative frequency unless only frequency is shown
	float manualMaxY; // relative frequency unless only frequency is shown

	// constraints
	static final int SampleCountDefault = 500;
	static final int SampleCountMinimum = 5;
	static final int SampleCountMaximum = Integer.MAX_VALUE;
	int sampleCount;

	static final int BinCountDefault = 60;
	static final int BinCountMinimum = 2;
	static final int BinCountMaximum = Integer.MAX_VALUE;

	static final float xAxisMinimumDefault = -1;
	static final float xAxisMaximumDefault = 1;
	static final float xAxisCenterDefault = 0;
	static final float xAxisLowerLimit = -Float.MAX_VALUE;
	static final float xAxisUpperLimit = Float.MAX_VALUE;

	static final float yAxisRelativeFrequencyMinimumDefault = 0;
	static final float yAxisRelativeFrequencyMaximumDefault = 1;
	static final float yAxisRelativeFrequencyLowerLimit = 0;
	static final float yAxisRelativeFrequencyUpperLimit = 1;

	static final int yAxisFrequencyMinimumDefault = 0;
	static final int yAxisFrequencyMaximumDefault = 1000;
	static final int yAxisFrequencyLowerLimit = 0;
	static final int yAxisFrequencyUpperLimit = Integer.MAX_VALUE;

	// Max Speed
	static final int maxSpeedWidgetDefault = 200;
	int topSpeed;

	static final int lowerSpeedLimit = -1;
	static final int upperSpeedLimit = Integer.MAX_VALUE;

	// control widgets
	WidgetDatasets datasetsWidget;
	WidgetTextfieldInteger sampleCountWidget;
	WidgetTextfieldInteger binCountWidget;
	WidgetTextfieldInteger topSpeedWidget;
	WidgetHistogramXaxisType xAxisTypeWidget;
	WidgetHistogramYaxisType yAxisTypeWidget;
	WidgetCheckbox showXaxisTitleWidget;
	WidgetCheckbox showXaxisScaleWidget;
	WidgetCheckbox showYaxisTitleWidget;
	WidgetCheckbox showYaxisScaleWidget;
	WidgetCheckbox showLegendWidget;
	AutoScale autoscalePower;

	// control widgets
	WidgetCheckbox showTextLabelWidget;
	boolean showTextLabel;

	// My points
	private ArrayList<Float> xCord;
	private ArrayList<Float> yCord;
	private ArrayList<Float> speed;
	private boolean firstTimeStartUp;

	public OpenGLDotPlot(int x1, int y1, int x2, int y2) {
		super(x1, y1, x2, y2);

		autoscalePower = new AutoScale(AutoScale.MODE_STICKY, 30, 0.20f);

		// Control Widgets
		datasetsWidget = new WidgetDatasets(3, new String[] { "X-Cord", "Y-Cord", "Speed" },
				newDatasets -> datasets = newDatasets);

		topSpeedWidget = new WidgetTextfieldInteger("Top Speed", maxSpeedWidgetDefault, lowerSpeedLimit,
				upperSpeedLimit, newTopSpeed -> topSpeed = newTopSpeed);

		sampleCountWidget = new WidgetTextfieldInteger("Sample Count", SampleCountDefault, lowerSpeedLimit,
				upperSpeedLimit, newTopSpeed -> sampleCount = newTopSpeed);

		showXaxisScaleWidget = new WidgetCheckbox("Show X-Axis Scale", true,
				newShowXaxisScale -> showXaxisScale = newShowXaxisScale);

		showYaxisScaleWidget = new WidgetCheckbox("Show Y-Axis Scale", true,
				newShowYaxisScale -> showYaxisScale = newShowYaxisScale);

		widgets = new Widget[5];

		widgets[0] = datasetsWidget;
		widgets[1] = topSpeedWidget;
		widgets[2] = sampleCountWidget;
		widgets[3] = showXaxisScaleWidget;
		widgets[4] = showYaxisScaleWidget;

		xCord = new ArrayList<>();
		yCord = new ArrayList<>();
		speed = new ArrayList<>();
		firstTimeStartUp = true;
	}

	@Override
	public EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, int lastSampleNumber,
			double zoomLevel, int mouseX, int mouseY) {

		/*
		 * Look into using a static hashmap which stores last positions of plot points,
		 * or just use a buffer...
		 */

		EventHandler handler = null;


		// Just to make it not crash
		sampleCount = Math.min(sampleCount, lastSampleNumber);

		// calculate x and y positions of everything
		xPlotLeft = Theme.tilePadding;
		xPlotRight = width - Theme.tilePadding;
		plotWidth = xPlotRight - xPlotLeft;
		yPlotTop = height - Theme.tilePadding;
		yPlotBottom = Theme.tilePadding;
		plotHeight = yPlotTop - yPlotBottom;

		// get the samples
		int endIndex = lastSampleNumber;
		int startIndex = endIndex - (int) (sampleCount * zoomLevel) + 1;
		int minDomain = SampleCountMinimum - 1;
		if (endIndex - startIndex < minDomain)
			startIndex = endIndex - minDomain;
		if (startIndex < 0)
			startIndex = 0;
		sampleCount = Math.min(endIndex - startIndex + 1, sampleCount);

		if (sampleCount - 1 < minDomain)
			return handler;

		boolean haveDatasets = datasets != null && !datasets.isEmpty();

		if (haveDatasets) {
			if (!firstTimeStartUp) {
				xCord.add(datasets.get(0).getSample(endIndex));
				yCord.add(datasets.get(1).getSample(endIndex));
				speed.add(datasets.get(2).getSample(endIndex));
				if (checkCollisions() && sampleCount == xCord.size()) {
					xCord.remove(0);
					yCord.remove(0);
					speed.remove(0);
				}
			} else {
				for (int i = startIndex; i < endIndex; i++) {
					xCord.add(datasets.get(0).getSample(i));
					yCord.add(datasets.get(1).getSample(i));
					speed.add(datasets.get(2).getSample(i));
				}
				firstTimeStartUp = false;
			}
		}
		while (xCord.size() > sampleCount) 
		{
			xCord.remove(0);
			yCord.remove(0);
			speed.remove(0);
		}

		int datasetsCount = Math.min(xCord.size(), Math.min(yCord.size(), speed.size()));

		float trueMinX = 0.0f;
		float trueMaxX = 0.0f;
		for (float x : xCord) {
			if (x < trueMinX)
				trueMinX = x;
			else if (x > trueMaxX)
				trueMaxX = x;
		}

		float trueMinY = 0.0f;
		float trueMaxY = 0.0f;
		for (float y : yCord) {
			if (y < trueMinY)
				trueMinY = y;
			else if (y > trueMaxY)
				trueMaxY = y;
		}

		float xDifference = (trueMaxX - trueMinX) * (1f / 10f);
		float yDifference = (trueMaxY - trueMinY) * (1f / 10f);

		trueMaxX += xDifference;
		trueMinX -= xDifference;
		trueMaxY += yDifference;
		trueMinY -= yDifference;

		// get the x divisions now that we know the final plot width
		xDivisions = ChartUtils.getFloatXdivisions125(gl, plotWidth, trueMinX, trueMaxX);
		// get the y divisions now that we know the final plot height
		yDivisions = ChartUtils.getYdivisions125(plotHeight, trueMinY, trueMaxY);

		if (showXaxisScale) {
			yXaxisTickTextBaseline = yPlotBottom;
			yXaxisTickTextTop = yXaxisTickTextBaseline + OpenGL.smallTextHeight;
			yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
			yXaxisTickTop = yXaxisTickBottom + Theme.tickLength;

			yPlotBottom = yXaxisTickTop;
			plotHeight = yPlotTop - yPlotBottom;
		}
		float smallTextMaxWidth = 0;
		if (showYaxisScale) {
			xYaxisTickTextBaseline = xPlotLeft;
			for (float y : yCord) {
				DecimalFormat df = new DecimalFormat("#.00");
				float textWidth = OpenGL.smallTextWidth(gl, Float.valueOf(df.format(y)) + "");
				if (textWidth + 1 > smallTextMaxWidth)
					smallTextMaxWidth = textWidth;
			}
			xYaxisTickTextTop = xYaxisTickTextBaseline + smallTextMaxWidth;
			xYaxisTickBottom = xYaxisTickTextTop + Theme.tickTextPadding;
			xYaxisTickTop = xYaxisTickBottom + Theme.tickLength;

			xPlotLeft = xYaxisTickTop;
			plotWidth = xPlotRight - xPlotLeft;
		}

		// clip to the plot region
		int[] originalScissorArgs = new int[4];

		OpenGL.drawQuad2D(gl, Theme.plotBackgroundColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);

		gl.glGetIntegerv(GL3.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom,
				(int) plotWidth, (int) plotHeight);

		// draw the points
		// Remove the damn ArrayList already and just upgrade to a Buffer - Allows more points and removed lag
		// ArrayList adds to runtime and decreases stability. Create a "Remove Outlier" function which can remove 
		// Outside of realistic values. Function will determine speed necessary to move from point 'A' to 'B', if
		// speed is too high then point is removed.
		if (haveDatasets) {
			for (int datasetN = 0; datasetN < datasetsCount; datasetN++) {
				float[] color = new float[] { 1f, 0f, 0f, speed.get(datasetN) / Math.max(topSpeed, 1) };
				float xValue = (xCord.get(datasetN) - trueMinX) / (trueMaxX - trueMinX) * plotWidth + xPlotLeft;
				float yValue = (yCord.get(datasetN) - trueMinY) / (trueMaxY - trueMinY) * plotHeight
						+ yPlotBottom;

				drawCircle(gl, color, xValue, yValue, 10);
			}
		}

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);

		// draw the x-axis scale
		if (showXaxisScale) {
			OpenGL.buffer.rewind();
			for (Float xValue : xDivisions.keySet()) {
				float x = ((xValue - trueMinX) / (trueMaxX - trueMinX) * plotWidth) + xPlotLeft;
				OpenGL.buffer.put(x);
				OpenGL.buffer.put(yPlotTop);
				OpenGL.buffer.put(Theme.divisionLinesColor);
				OpenGL.buffer.put(x);
				OpenGL.buffer.put(yPlotBottom);
				OpenGL.buffer.put(Theme.divisionLinesColor);

				OpenGL.buffer.put(x);
				OpenGL.buffer.put(yXaxisTickTop);
				OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(x);
				OpenGL.buffer.put(yXaxisTickBottom);
				OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = xDivisions.keySet().size() * 4;
			OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);

			for (Map.Entry<Float, String> entry : xDivisions.entrySet()) {
				float x = ((entry.getKey() - trueMinX) / (trueMaxX - trueMinX) * plotWidth) + xPlotLeft
						- (OpenGL.smallTextWidth(gl, entry.getValue()) / 2.0f);
				float y = yXaxisTickTextBaseline;
				OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
			}
		}

		/*
		 * boolean showYaxisScale; Map<Float, String> yDivisions; float
		 * xYaxisTickTextBaseline; float xYaxisTickTextTop; float xYaxisTickBottom;
		 * float xYaxisTickTop;
		 */

		// draw the y-axis scale
		if (showYaxisScale) {
			// draw right y-axis scale if showing both frequency and relative frequency
			OpenGL.buffer.rewind();
			for (Float entry : yDivisions.keySet()) {
				float y = (entry - trueMinY) / (trueMaxY - trueMinY) * plotHeight + yPlotBottom;
				OpenGL.buffer.put(xPlotRight);
				OpenGL.buffer.put(y);
				OpenGL.buffer.put(Theme.divisionLinesColor);
				OpenGL.buffer.put(xPlotLeft);
				OpenGL.buffer.put(y);
				OpenGL.buffer.put(Theme.divisionLinesFadedColor);

				OpenGL.buffer.put(xYaxisTickTop);
				OpenGL.buffer.put(y);
				OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(xYaxisTickBottom);
				OpenGL.buffer.put(y);
				OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = yDivisions.keySet().size() * 4;
			OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);

			for (Map.Entry<Float, String> entry : yDivisions.entrySet()) {
				float x = xPlotLeft - smallTextMaxWidth;
				float y = (entry.getKey() - trueMinY) / (trueMaxY - trueMinY) * plotHeight + yPlotBottom
						- (OpenGL.smallTextHeight / 2.0f);
				OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
			}
		}

		// draw the plot border & background
		OpenGL.drawQuadOutline2D(gl, Theme.plotOutlineColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);

		return handler;
	}

	private boolean checkCollisions() // ReWrite
	{
		float trueMinX = 0.0f;
		float trueMaxX = 0.0f;
		for (float x1 : xCord) {
			if (x1 < trueMinX)
				trueMinX = x1;
			else if (x1 > trueMaxX)
				trueMaxX = x1;
		}

		float trueMinY = 0.0f;
		float trueMaxY = 0.0f;
		for (float y1 : yCord) {
			if (y1 < trueMinY)
				trueMinY = y1;
			else if (y1 > trueMaxY)
				trueMaxY = y1;
		}

		float xDifference = (trueMaxX - trueMinX) * (1f / 10f);
		float yDifference = (trueMaxY - trueMinY) * (1f / 10f);

		trueMaxX += xDifference;
		trueMinX -= xDifference;
		trueMaxY += yDifference;
		trueMinY -= yDifference;
		
		float xValue1 = (xCord.get(xCord.size() - 1) - trueMinX) / (trueMaxX - trueMinX) * plotWidth + xPlotLeft;
		float yValue1 = (yCord.get(xCord.size() - 1) - trueMinY) / (trueMaxY - trueMinY) * plotHeight;
		float xValue2 = (xCord.get(xCord.size() - 2) - trueMinX) / (trueMaxX - trueMinX) * plotWidth + xPlotLeft;
		float yValue2 = (yCord.get(xCord.size() - 2) - trueMinY) / (trueMaxY - trueMinY) * plotHeight;
		
		float xDist = Math.abs(xValue1 - xValue2);
		float yDist = Math.abs(yValue1 - yValue2);
		float dist = (float) Math.pow(Math.pow(xDist, 2) + Math.pow(yDist, 2), 2);

		if (dist > 9f)
			return true;
		xCord.remove(xCord.size() - 1);
		yCord.remove(yCord.size() - 1);
		speed.remove(speed.size() - 1);
		return false;
	}

	public static void drawCircle(GL2ES3 gl, float[] color, float x, float y, int radius) {
		double increment = 2 * Math.PI / 10;

		// Draw a bunch of triangles
		for (double angle = 0; angle < 2 * Math.PI; angle += increment) {
			float x1 = x + (float) Math.cos(angle) * radius;
			float y1 = y + (float) Math.sin(angle) * radius;
			float x2 = x + (float) Math.cos(angle + increment) * radius;
			float y2 = y + (float) Math.sin(angle + increment) * radius;

			OpenGL.drawTriangle2D(gl, color, x, y, x1, y1, x2, y2);
		}
	}

	@Override
	public String toString() {
		return "Dot Plot";
	}
}