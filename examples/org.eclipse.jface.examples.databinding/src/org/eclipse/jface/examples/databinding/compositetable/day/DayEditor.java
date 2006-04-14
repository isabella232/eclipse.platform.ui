/*******************************************************************************
 * Copyright (c) 2006 The Pampered Chef and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     The Pampered Chef - initial API and implementation
 ******************************************************************************/
package org.eclipse.jface.examples.databinding.compositetable.day;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jface.examples.databinding.compositetable.CompositeTable;
import org.eclipse.jface.examples.databinding.compositetable.IRowContentProvider;
import org.eclipse.jface.examples.databinding.compositetable.RowConstructionListener;
import org.eclipse.jface.examples.databinding.compositetable.ScrollEvent;
import org.eclipse.jface.examples.databinding.compositetable.ScrollListener;
import org.eclipse.jface.examples.databinding.compositetable.day.internal.CalendarableEventControl;
import org.eclipse.jface.examples.databinding.compositetable.day.internal.DayLayout;
import org.eclipse.jface.examples.databinding.compositetable.day.internal.DayLayoutsByDate;
import org.eclipse.jface.examples.databinding.compositetable.day.internal.DayModel;
import org.eclipse.jface.examples.databinding.compositetable.day.internal.TimeSlice;
import org.eclipse.jface.examples.databinding.compositetable.timeeditor.Calendarable;
import org.eclipse.jface.examples.databinding.compositetable.timeeditor.CalendarableModel;
import org.eclipse.jface.examples.databinding.compositetable.timeeditor.EventContentProvider;
import org.eclipse.jface.examples.databinding.compositetable.timeeditor.EventCountProvider;
import org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

/**
 * A DayEditor is an SWT control that can display events on a time line that can
 * span one or more days.  This class is not intended to be subclassed.
 * 
 * @since 3.2
 */
public class DayEditor extends Composite implements IEventEditor {
	/**
	 * The default start hour.  Normally 8:00 AM
	 */
	private CompositeTable compositeTable = null;
	private CalendarableModel model = new CalendarableModel();
	private DayLayoutsByDate dayLayoutsByDate;
	private List spareCalendarableEventControls = new LinkedList();
	protected TimeSlice daysHeader;

	/**
	 * Constructor DayEditor.  Constructs a calendar control that can display
	 * events on one or more days.
	 * 
	 * @param parent
	 * @param style
	 */
	public DayEditor(Composite parent, int style) {
		super(parent, style);
		setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor#setTimeBreakdown(int, int)
	 */
	public void setTimeBreakdown(int numberOfDays, int numberOfDivisionsInHour) {
		model.setTimeBreakdown(numberOfDays, numberOfDivisionsInHour);
		
		if (compositeTable != null) {
			compositeTable.dispose();
		}
		
		createCompositeTable(numberOfDays, numberOfDivisionsInHour);
		dayLayoutsByDate = new DayLayoutsByDate(model.getStartDate(), model.getNumberOfDays());
	}

	/**
	 * This method initializes compositeTable
	 * 
	 * @param numberOfDays
	 *            The number of day columns to display
	 */
	private void createCompositeTable(final int numberOfDays,
			final int numberOfDivisionsInHour) {
		compositeTable = new CompositeTable(this, SWT.NONE);
		new TimeSlice(compositeTable, SWT.BORDER);		// The prototype header
		new TimeSlice(compositeTable, SWT.NONE); // The prototype row
		
		compositeTable.setNumRowsInCollection(computeNumRowsInCollection(numberOfDivisionsInHour));
		
		compositeTable.addRowConstructionListener(new RowConstructionListener() {
			public void headerConstructed(Control newHeader) {
				daysHeader = (TimeSlice) newHeader;
				daysHeader.setHeaderControl(true);
				daysHeader.setNumberOfColumns(numberOfDays);
				if (model.getStartDate() == null) {
					return;
				}
				refreshColumnHeaders(daysHeader.getColumns());
			}
			
			public void rowConstructed(Control newRow) {
				TimeSlice timeSlice = (TimeSlice) newRow;
				timeSlice.setNumberOfColumns(numberOfDays);
			}
		});
		compositeTable.addRowContentProvider(new IRowContentProvider() {
			public void refresh(CompositeTable sender, int currentObjectOffset,
					Control row) {
				TimeSlice timeSlice = (TimeSlice) row;
				refreshRow(currentObjectOffset, timeSlice);
			}
		});
		compositeTable.addScrollListener(new ScrollListener() {
			public void tableScrolled(ScrollEvent scrollEvent) {
				layoutEventControls();
			}
		});
		addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				Rectangle bounds = DayEditor.this.getBounds();
				compositeTable.setBounds(0, 0, bounds.width, bounds.height);
				layoutEventControls();
			}
		});
		
		compositeTable.setRunTime(true);
	}

	/**
	 * @return Returns the defaultStartHour.
	 */
	public int getDefaultStartHour() {
		return model.getDefaultStartHour();
	}

	/**
	 * @param defaultStartHour The defaultStartHour to set.
	 */
	public void setDefaultStartHour(int defaultStartHour) {
		model.setDefaultStartHour(defaultStartHour);
		updateVisibleRows();
		layoutEventControls();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor#setDayEventCountProvider(org.eclipse.jface.examples.databinding.compositetable.timeeditor.EventCountProvider)
	 */
	public void setDayEventCountProvider(EventCountProvider eventCountProvider) {
		model.setDayEventCountProvider(eventCountProvider);
		updateVisibleRows();
		layoutEventControls();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor#setEventContentProvider(org.eclipse.jface.examples.databinding.compositetable.timeeditor.EventContentProvider)
	 */
	public void setEventContentProvider(EventContentProvider eventContentProvider) {
		model.setEventContentProvider(eventContentProvider);
		updateVisibleRows();
		layoutEventControls();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor#setStartDate(java.util.Date)
	 */
	public void setStartDate(Date startDate) {
		model.setStartDate(startDate);
		refreshColumnHeaders(daysHeader.getColumns());
		updateVisibleRows();
		refreshCalendarableEventControls();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor#getStartDate()
	 */
	public Date getStartDate() {
		return model.getStartDate();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor#refresh(java.util.Date)
	 */
	public void refresh(Date date) {
		model.refresh(date);
		updateVisibleRows();
		layoutEventControls();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor#getNumberOfDays()
	 */
	public int getNumberOfDays() {
		return model.getNumberOfDays();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.examples.databinding.compositetable.timeeditor.IEventEditor#getNumberOfDivisionsInHour()
	 */
	public int getNumberOfDivisionsInHour() {
		return model.getNumberOfDivisionsInHour();
	}
	
	// Display Refresh logic here ----------------------------------------------
	
	private int numberOfAllDayEventRows = 0;
	Calendar calendar = new GregorianCalendar();

	private int computeNumRowsInCollection(final int numberOfDivisionsInHour) {
		numberOfAllDayEventRows = model.computeNumberOfAllDayEventRows();
		return (DISPLAYED_HOURS-model.computeStartHour()) * numberOfDivisionsInHour+numberOfAllDayEventRows;
	}
	
	private int computeHourFromRow(int currentObjectOffset) {
		return currentObjectOffset / getNumberOfDivisionsInHour() + model.computeStartHour();
	}

	private int computeMinuteFromRow(int currentObjectOffset) {
		int numberOfDivisionsInHour = getNumberOfDivisionsInHour();
		int minute = (int) ((double) currentObjectOffset
				% numberOfDivisionsInHour
				/ numberOfDivisionsInHour * 60);
		return minute;
	}

	/*
	 * Update the number of rows that are displayed inside the CompositeTable control
	 */
	private void updateVisibleRows() {
		compositeTable.setNumRowsInCollection(computeNumRowsInCollection(getNumberOfDivisionsInHour()));
	}
	
	private void refreshRow(int currentObjectOffset, TimeSlice timeSlice) {
		// Decrement currentObjectOffset for each all-day event line we need.
		for (int allDayEventRow = 0; allDayEventRow < numberOfAllDayEventRows; ++allDayEventRow) {
			--currentObjectOffset;
		}
		
		if (currentObjectOffset < 0) {
			timeSlice.setCurrentTime(null);
		} else {
			calendar.set(Calendar.HOUR_OF_DAY, 
					computeHourFromRow(currentObjectOffset));
			calendar.set(Calendar.MINUTE,
					computeMinuteFromRow(currentObjectOffset));
			timeSlice.setCurrentTime(calendar.getTime());
		}
	}

	/**
	 * (non-API) Method initializeColumnHeaders.  Called internally when the
	 * column header text needs to be updated.
	 * 
	 * @param columns A LinkedList of CLabels representing the column objects
	 */
	protected void refreshColumnHeaders(LinkedList columns) {
		Date startDate = getStartDate();
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTime(startDate);

		SimpleDateFormat formatter = new SimpleDateFormat("EEEE, MMMM d");
		formatter.applyPattern(formatter.toLocalizedPattern());
		
		for (Iterator iter = columns.iterator(); iter.hasNext();) {
			CLabel headerLabel = (CLabel) iter.next();
			headerLabel.setText(formatter.format(gc.getTime()));
			gc.add(Calendar.DAY_OF_MONTH, 1);
		}
	}
	
	/**
	 * Make the correct event controls visible for the segment in time that
	 * we are currently displaying and resize them so that they occupy the
	 * correct portions of their day columns.
	 */
	private void refreshCalendarableEventControls() {
		freeObsoleteCalendarableEventControls();
		findEventRowsForNewDays();
		layoutEventControls();
	}

	private void freeObsoleteCalendarableEventControls() {
		List removedDays = dayLayoutsByDate.adjustStartDate(getStartDate());
		for (Iterator removedDaysIter = removedDays.iterator(); removedDaysIter.hasNext();) {
			DayLayout dayLayout = (DayLayout) removedDaysIter.next();
			for (Iterator calendarableIter = dayLayout.model.iterator(); calendarableIter.hasNext();) {
				Calendarable toRemove = (Calendarable) calendarableIter.next();
				freeCalendarableControl(toRemove);
			}
		}
	}
	
	private void findEventRowsForNewDays() {
		DayModel dayLayoutFactory = new DayModel(model.getNumberOfDivisionsInHour());
		for (int day=0; day < model.getNumberOfDays(); ++day) {
			Date currentDate = DayLayoutsByDate.addDaysToDate(getStartDate(), day);
			if (dayLayoutsByDate.get(currentDate) == null) {
				List events = model.getCalendarableEvents(day);
				Calendarable[][] layout = dayLayoutFactory.computeEventLayout(events);
				dayLayoutsByDate.put(currentDate, new DayLayout(events, layout));
			}
		}
	}

	private void layoutEventControls() {
		if (getStartDate() == null) {
			return;
		}
		while(Display.getCurrent().readAndDispatch()) {}	// A hack to make sure that the asyncExec runs immediately
//		Display.getCurrent().asyncExec(new Runnable() {
//			public void run() {
				Control[] gridRows = compositeTable.getRowControls();
				
				for (int day=0; day < model.getNumberOfDays(); ++day) {
					Date currentDate = DayLayoutsByDate.addDaysToDate(getStartDate(), day);
					DayLayout layoutForDay = dayLayoutsByDate.get(currentDate);
					Point[] columnPositions = computeColumns(day, layoutForDay.layout.length, gridRows);
					
					int allDayEventRow = 0;
					
					for (Iterator dayControlsIter = layoutForDay.model.iterator(); dayControlsIter.hasNext();) {
						Calendarable calendarable = (Calendarable) dayControlsIter.next();
						if (calendarable.isAllDayEvent()) {
							layoutAllDayEvent(day, allDayEventRow, calendarable, gridRows);
							++allDayEventRow;
						} else {
//							layoutTimedEvent(day, columnPositions, calendarable, gridRows);
						}
					}
				}
//			}
//		});
	}
	
	protected Point[] computeColumns(int day, int numberOfColumns, Control[] gridRows) {
		Point[] columns = new Point[numberOfColumns];
		Rectangle timeSliceBounds = getTimeSliceBounds(day, compositeTable.getTopRow(), gridRows);
		
		int baseWidth = timeSliceBounds.width / numberOfColumns;
		int extraWidth = timeSliceBounds.width % numberOfColumns;
		
		int startingPosition = timeSliceBounds.x;
		for (int column = 0; column < columns.length; column++) {
			int columnStart = startingPosition;
			int columnWidth = baseWidth;
			if (extraWidth > 0) {
				++columnWidth;
				--extraWidth;
			}
			columns[column] = new Point(columnStart, columnWidth);
			startingPosition += columnWidth;
		}
		return columns;
	}

	private void fillControlData(Calendarable calendarable) {
		calendarable.getControl().setText(calendarable.getText());
	}

	private void layoutAllDayEvent(int day, int allDayEventRow, Calendarable calendarable, Control[] gridRows) {
		if (eventRowIsVisible(allDayEventRow)) {
			createCalendarableControl(calendarable);
			fillControlData(calendarable);
			
			Rectangle timeSliceBounds = getTimeSliceBounds(day, allDayEventRow, gridRows);
			calendarable.getControl().setBounds(timeSliceBounds);
			calendarable.getControl().moveAbove(compositeTable);
		} else {
			freeCalendarableControl(calendarable);
		}
	}

	private void layoutTimedEvent(int day, Point[] columnPositions, Calendarable calendarable, Control[] gridRows) {
		int firstVisibleRow = model.computeStartHour() * model.getNumberOfDivisionsInHour();
		
		int scrolledRows = compositeTable.getTopRow() - numberOfAllDayEventRows;
		int visibleAllDayEventRows = 0;
		if (scrolledRows < 0) {
			visibleAllDayEventRows = -1 * scrolledRows;
			scrolledRows = 0;
		}
		firstVisibleRow += scrolledRows;
		int lastVisibleRow = firstVisibleRow + compositeTable.getNumRowsVisible() - visibleAllDayEventRows - 1;
		
		int startRow = calendarable.getUpperLeftPositionInDayRowCoordinates().y;
		int endRow = calendarable.getLowerRightPositionInDayRowCoordinates().y;
		
		if (timedEventIsVisible(calendarable, firstVisibleRow, lastVisibleRow, startRow, endRow)) {
			if (startRow < firstVisibleRow)
				startRow = firstVisibleRow;
			
			if (endRow > lastVisibleRow)
				endRow = lastVisibleRow;
			
			firstVisibleRow = convertDayRowToViewportCoordinates(firstVisibleRow);
			lastVisibleRow = convertDayRowToViewportCoordinates(lastVisibleRow);
			startRow = convertDayRowToViewportCoordinates(startRow);
			endRow = convertDayRowToViewportCoordinates(endRow);
			
			createCalendarableControl(calendarable);
			fillControlData(calendarable);
			
			Rectangle startRowBounds = getTimeSliceBounds(day, startRow, gridRows);
			Rectangle endRowBounds = getTimeSliceBounds(day, endRow, gridRows);
			
			int leftmostColumn = calendarable.getUpperLeftPositionInDayRowCoordinates().x;
			int rightmostColumn = calendarable.getLowerRightPositionInDayRowCoordinates().x;
			
			int left = columnPositions[leftmostColumn].x;
			int top = startRowBounds.y;
			int width = columnPositions[rightmostColumn].x - columnPositions[leftmostColumn].x + columnPositions[rightmostColumn].y;
			int height = endRowBounds.y - startRowBounds.y + endRowBounds.height;
			
			Rectangle finalPosition = new Rectangle(left, top, width, height);
			
			calendarable.getControl().setBounds(finalPosition);
			calendarable.getControl().moveAbove(compositeTable);
		} else {
			freeCalendarableControl(calendarable);
		}
	}

	private int convertDayRowToViewportCoordinates(int row) {
		row -= model.computeStartHour() * model.getNumberOfDivisionsInHour()
			- numberOfAllDayEventRows;
		return row;
	}
	
	private boolean eventRowIsVisible(int eventRow) {
		if (compositeTable.getTopRow() <= eventRow) {
			return true;
		}
		return false;
	}
	
	private boolean timedEventIsVisible(Calendarable calendarable, int firstVisibleRow, int lastVisibleRow, int startRow, int endRow) {
		if (startRow < firstVisibleRow && endRow < firstVisibleRow)
			return false;
		
		if (startRow > lastVisibleRow && endRow > lastVisibleRow)
			return false;
		
		return true;
	}

	private void createCalendarableControl(Calendarable calendarable) {
		if (calendarable.getControl() == null) {
			calendarable.setControl(newCEC());
		}
	}
	
	private Rectangle getTimeSliceBounds(int day, int eventRow, Control[] gridRows) {
		TimeSlice rowObject = (TimeSlice) gridRows[eventRow - compositeTable.getTopRow()];
		Control slot = rowObject.getColumnControl(day);
		return getBoundsInDayEditorCoordinates(slot);
	}
	
	private void freeCalendarableControl(Calendarable calendarable) {
		if (calendarable.getControl() != null) {
			freeCEC(calendarable.getControl());
			calendarable.setControl(null);
		}
	}
	
	private Rectangle getBoundsInDayEditorCoordinates(Control slot) {
		return Display.getCurrent().map(slot.getParent(), this, slot.getBounds());
	}

	// CalendarableEventControl construction/destruction here -----------------

	private CalendarableEventControl newCEC() {
		if (spareCalendarableEventControls.size() > 0) {
			CalendarableEventControl result = (CalendarableEventControl) spareCalendarableEventControls.remove(0);
			result.setVisible(true);
			return result;
		}
		return new CalendarableEventControl(this, SWT.NULL);
	}
	
	private void freeCEC(CalendarableEventControl control) {
		control.setVisible(false);
		spareCalendarableEventControls.add(control);
	}

} // @jve:decl-index=0:visual-constraint="10,10"


