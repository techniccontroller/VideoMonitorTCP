package application;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

import utils.Utils;

public class Main extends Application {

	// GUI elements
	private ImageView imageView;
	private Button btnStart;

	// a timer for acquiring the video stream
	private ScheduledExecutorService timer;

	// a flag to change the button behavior
	private boolean cameraActive = false;

	// Socket
	private Socket clientSocket;
	private OutputStreamWriter outToServer;
	private InputStreamReader inFromServer;

	@Override
	public void start(Stage primaryStage) {
		try {
			BorderPane root = new BorderPane();

			Image image = getBlankImage(320, 240);
			imageView = new ImageView(image);
			imageView.setFitHeight(240);
			imageView.setFitWidth(320);
			root.setTop(imageView);

			btnStart = new Button("Start Camera");
			btnStart.setPrefWidth(100);
			btnStart.setPrefHeight(30);
			btnStart.setOnAction(e -> {
				handleButtonPress();
			});
			root.setBottom(btnStart);

			Scene scene = new Scene(root);
			primaryStage.setTitle("Video Monitor");
			primaryStage.setScene(scene);
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

private class CameraRunnable implements Runnable {

	@Override
	public void run() {
		String message = "getNewFrame";
		try {
			if (cameraActive) {
				long startTime = System.currentTimeMillis();
				outToServer.write(message);
				outToServer.flush();
				char[] sizeAr = new char[16];
				inFromServer.read(sizeAr);
				int size = Integer.valueOf(new String(sizeAr).trim());
				// System.out.println(size);
				char[] data = new char[size];
				int pos = 0;
				do {
					int read = inFromServer.read(data, pos, size - pos);
					// check for end of file or error
					if (read == -1) {
						break;
					} else {
						pos += read;
					}
				} while (pos < size);
				long timeToGrab = System.currentTimeMillis() - startTime;
				System.out.println(timeToGrab);
				String encoded = new String(data);
				byte[] decoded = Base64.getDecoder().decode(encoded);

				BufferedImage image = ImageIO.read(new ByteArrayInputStream(decoded));

				// convert and show the frame
				Image imageToShow = SwingFXUtils.toFXImage(image, null);
				updateImageView(imageView, imageToShow);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

protected void handleButtonPress() {

	if (!this.cameraActive) {

		try {
			if (clientSocket == null || clientSocket.isClosed()) {
				clientSocket = new Socket("192.168.0.116", 5001);
				outToServer = new OutputStreamWriter(clientSocket.getOutputStream());
				inFromServer = new InputStreamReader(clientSocket.getInputStream());
			}

			// grab a frame every 33 ms (30 frames/sec)
			CameraRunnable frameGrabber = new CameraRunnable();
			this.timer = Executors.newSingleThreadScheduledExecutor();
			this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

			// update the button content
			this.btnStart.setText("Stop Camera");
			this.cameraActive = true;
		} catch (IOException e) {
			System.err.println("Impossible to open the camera connection...");
			e.printStackTrace();
		}
	} else {
		// the camera is not active at this point
		this.cameraActive = false;

		// update again the button content
		this.btnStart.setText("Start Camera");

		// stop the timer
		this.stopAcquisition();

		System.out.println("Close Socket...");
		try {
			while (!timer.isShutdown()) {
				clientSocket.shutdownOutput();
				clientSocket.close();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}

	/**
	 * 
	 * Stop the acquisition from the camera and release all the resources
	 * 
	 */
private void stopAcquisition(){
	if (this.timer != null && !this.timer.isShutdown()) {
		try {
			// stop the timer
			this.timer.shutdown();
			this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
		}

		catch (InterruptedException e) {
			// log any exception
			e.printStackTrace();
		}
	}
}

	/**
	 * 
	 * Update the {@link ImageView} in the JavaFX main thread
	 * 
	 * 
	 * @param view  the {@link ImageView} to update
	 * @param image the {@link Image} to show
	 */
	private void updateImageView(ImageView view, Image image)

	{
		Utils.onFXThread(view.imageProperty(), image);
	}

	protected Image getBlankImage(int width, int height) {
		BufferedImage bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = (Graphics2D) bImage.getGraphics();

		// Clear the background with white
		g.setBackground(Color.BLACK);
		g.clearRect(0, 0, width, height);

		// Write some text
		g.setColor(Color.WHITE);
		Font font = new Font("Tahoma", Font.PLAIN, 28);
		g.setFont(font);
		g.drawString("Camera is OFF", width / 2 - 100, height / 2 - 20);

		g.dispose();
		return SwingFXUtils.toFXImage(bImage, null);
	}

	public static void main(String[] args) {
		launch(args);
	}
}
