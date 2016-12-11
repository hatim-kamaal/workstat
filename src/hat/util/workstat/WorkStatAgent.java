package hat.util.workstat;

import java.awt.CheckboxMenuItem;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

/**
 * 
 * @author Hatim Kamaal
 *
 */
public class WorkStatAgent {

	private Timer timer;
	private Connection conn;
	private Logger log = Logger.getLogger("Test");

	public WorkStatAgent() throws Exception {

		setupLogging();

		log.log(Level.FINEST, "Logging is configured.");
		log.log(Level.FINEST, "Adding program icon to system tray.");
		
		// Create instance in System tray.
		addToSystemTray();
		
		// Configure the database.
		configureDB();

		// Start the stats collector.
		startStatCollector();
	}

	/**
	 * 
	 */
	private void setupLogging() {
		//Handle exception to make sure proper logging.
		try {
			log.setLevel(Level.ALL);
			Handler fileHandler = new FileHandler("workstat.log");
			fileHandler.setLevel(Level.ALL);
			fileHandler.setFormatter(new Formatter() {
				@Override
				public synchronized String format(LogRecord record) {
					System.out.println(record.getLevel() +" : " +  record.getMessage());
					return String.format(record.getMessage() + "%n");
				}
			});
			log.addHandler(fileHandler);
		} catch (Exception e) {
			System.out.println("Error setting up logging. Program terminated.");
			e.printStackTrace();
			System.exit(-1);
		}

	}

	public byte[] extractBytes(String ImageName) throws IOException {
		// open image
		File imgPath = new File(ImageName);
		BufferedImage bufferedImage = ImageIO.read(imgPath);
		// get DataBufferBytes from Raster
		WritableRaster raster = bufferedImage.getRaster();
		DataBufferByte data = (DataBufferByte) raster.getDataBuffer();

		return (data.getData());
	}

	private void addToSystemTray() {
		// Check the SystemTray is supported
		if (!SystemTray.isSupported()) {
			//System.out.println("SystemTray is not supported");
			log.log(Level.SEVERE, "System tray is not supported. Program can't work without it. Program terminated.");
			System.exit(-1);
		}

		try {
			final PopupMenu popup = new PopupMenu();

			BufferedImage trayIconImage = ImageIO.read(new File("idea-xxl.png"));
			 /*BufferedImage trayIconImage =
			 ImageIO.read(getClass().getResource("idea-xxl.png"));*/
			int trayIconWidth = new TrayIcon(trayIconImage).getSize().width;
			final TrayIcon trayIcon = new TrayIcon(trayIconImage.getScaledInstance(trayIconWidth, -1, Image.SCALE_SMOOTH),
					"Work Stat Monitor");

			final SystemTray tray = SystemTray.getSystemTray();

			// Create a pop-up menu components
			MenuItem aboutItem = new MenuItem("About");
			aboutItem.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					JOptionPane.showMessageDialog(null, "Work Stat Monitor version 0.01, developed by Hatim Kamaal.");
				}
			});
			CheckboxMenuItem cb1 = new CheckboxMenuItem("Set auto size");
			CheckboxMenuItem cb2 = new CheckboxMenuItem("Set tooltip");
			Menu displayMenu = new Menu("Display");
			MenuItem errorItem = new MenuItem("Error");
			MenuItem warningItem = new MenuItem("Warning");
			MenuItem infoItem = new MenuItem("Info");
			MenuItem noneItem = new MenuItem("None");
			MenuItem exitItem = new MenuItem("Exit");
			exitItem.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					log.log(Level.SEVERE, "User forced the termination. Program terminated.");
					System.exit(0);
				}
			});

			// Add components to pop-up menu
			popup.add(aboutItem);
			popup.addSeparator();
			popup.add(cb1);
			popup.add(cb2);
			popup.addSeparator();
			popup.add(displayMenu);
			displayMenu.add(errorItem);
			displayMenu.add(warningItem);
			displayMenu.add(infoItem);
			displayMenu.add(noneItem);
			popup.add(exitItem);

			trayIcon.setPopupMenu(popup);
			
			tray.add(trayIcon);
		} catch (Exception e) {
			log.log(Level.SEVERE, "TrayIcon could not be added. Program terminated.");
			System.exit(-1);
		}

	}

	private void configureDB() throws Exception {
		Class.forName("org.hsqldb.jdbc.JDBCDriver");
		conn = DriverManager.getConnection("jdbc:hsqldb:file:ntt/hatim;hsqldb.write_delay=false;", "hatim", "hatim");

		// dbm = DataManager.getInst();
		try {
			PreparedStatement pstmt = conn.prepareStatement("SELECT timeinmillis, state FROM workstat LIMIT ?");
			pstmt.setInt(1, 1);
			pstmt.executeQuery();
			System.out.println("Table exist ...");
			pstmt.close();
		} catch (Exception e) {
			PreparedStatement pstmt = conn
					.prepareStatement("CREATE TABLE workstat ( timeinmillis bigint, state varchar(20))");
			if (pstmt.executeUpdate() == 0) {
				System.out.println("Table is created.");
			} else {
				System.out.println("Error --- Table creation.");
			}
			pstmt.close();
		}
	}

	public static void main(String[] args) throws Exception {
		new WorkStatAgent();
	}

	/**
	 * 
	 */
	private void startStatCollector() {
		timer = new Timer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				try {
					ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "cscript SystemStat.vbs");
					builder.redirectErrorStream(true);
					Process p = builder.start();
					BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
					String line;
					while ((line = r.readLine()) != null) {
						if (line.startsWith("======>")) {
							PreparedStatement pstmt = conn
									.prepareStatement("INSERT INTO workstat(timeinmillis, state) VALUES(?,?)");
							pstmt.setLong(1, Calendar.getInstance().getTimeInMillis());
							pstmt.setString(2, line.trim().substring(7));
							if (pstmt.executeUpdate() == -1) {
								System.out.println("Data is NOT inserted..");
							} else {
								System.out.println("Data is inserted..");
							}
							pstmt.close();
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		}, 0, 1000);

	}

}
