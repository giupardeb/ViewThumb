import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.frame.PlugInFrame;

public class ViewThumb_ extends PlugInFrame implements ActionListener{

	private static final long serialVersionUID = -8128661425506940841L;

	private BufferedImage immagineOriginal = null;
	private BufferedImage immagineThumbnail = null;
	private BufferedImage imageScaled = null;
	private ImagePlus CurrentImage;

	private JButton btnCompare;
	private JButton btnChangeThumb;
	private static Frame instance;

	private ThumbStream thumbnail;
	private File destinationThumbnail;
	private String destinationImageOriginal;

	
	public ViewThumb_() {
		super("CmpThumb");
		// TODO Auto-generated constructor stub
		if (instance != null) {
			instance.toFront();
			return;
		}

	}

	@Override
	public void run(String arg0) {	
		// TODO Auto-generated method stub

		CurrentImage = WindowManager.getCurrentImage();
		if (CurrentImage == null){

			IJ.beep();
			IJ.showStatus("No image");
			IJ.noImage();
			return;
		}

		try{
			if(inizializzaImage())
				inizializzaGUI();
			else return;
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		btnCompare.addActionListener(this);
		btnChangeThumb.addActionListener(this);

	}

	////////////////////////////////////
	//        METODI PRIVATI          //
	////////////////////////////////////

	void inizializzaGUI() throws IOException{

		JFrame frame = new JFrame();

		File FileimageScaled = new File(Prefs.getImageJDir()+"images/imageScaled.jpg");
		ImageIO.write(this.imageScaled, "jpg", FileimageScaled);


		btnCompare = new JButton("Compare");
		btnChangeThumb = new JButton("Change Thumbnail");
		btnChangeThumb.setEnabled(false);

		frame.setSize(500, 500);
		frame.setResizable(false);

		JPanel thumbnail = addImage(destinationThumbnail.toString(),true);
		JPanel original = addImage(FileimageScaled.getAbsolutePath(),false);

		frame.getContentPane().add(thumbnail, BorderLayout.LINE_START);
		frame.getContentPane().add(original, BorderLayout.LINE_END);
		frame.getContentPane().add(addButton(btnCompare,btnChangeThumb), BorderLayout.PAGE_END);
		
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	public JPanel addButton(JButton btnCompare, JButton btnChangeThumb) {
		JPanel inner = new JPanel();
		inner.setLayout(new GridLayout(1, 2, 10, 0));
		inner.add(btnCompare);
		inner.add(btnChangeThumb);
		return inner;

	}

	public JPanel addImage(String path, boolean isThumbnail){

		BufferedImage myPicture = null;
		JPanel inner = new JPanel();

		try {
			myPicture = ImageIO.read(new File(path));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		JLabel picLabel = new JLabel(new ImageIcon(myPicture));

		inner.add(picLabel);

		if(isThumbnail) 
			picLabel.setToolTipText("Thumbnail");

		else 
			picLabel.setToolTipText("Originale");

		return inner;
	}

	/**
	 * recupera l'immagine originale, estrae la sua thumbnail e scala l'immagine originale per il confronto
	 * @throws IOException 
	 */
	private boolean inizializzaImage() throws IOException {

		//Immagine scafricata da internet attraverso dei Samples di ImageJ

		if (CurrentImage.getOriginalFileInfo().fileName.equalsIgnoreCase("Untitled") && 
				!(CurrentImage.getOriginalFileInfo().url.equalsIgnoreCase(""))){

			destinationImageOriginal = fileDownload(CurrentImage.getOriginalFileInfo().url,"images/");
			File img = new File(destinationImageOriginal);
			try {
				immagineOriginal = ImageIO.read(img);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		//Immagine presa e aperta da disco fisso

		else{
			destinationImageOriginal = CurrentImage.getOriginalFileInfo().directory + CurrentImage.getOriginalFileInfo().fileName;
			immagineOriginal = CurrentImage.getBufferedImage();
			CurrentImage.close();
		}

		try {
			thumbnail = new ThumbStream(destinationImageOriginal);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			GenericDialog gd = new GenericDialog("Errore");
			gd.setType(Window.Type.POPUP);
			gd.add(new JLabel("L'immagine selezionata non ha nessun dato EXIF"));
			gd.showDialog();
			return false;			
		}

		destinationThumbnail = new File(destinationImageOriginal.substring(0, destinationImageOriginal.length()-4)+ "_thumb.jpg");

		FileOutputStream out = null;

		out = new FileOutputStream(destinationThumbnail.getAbsolutePath());

		byte[] buffer = new byte[1024];
		int bytesRead = 0;

		while((bytesRead = thumbnail.read(buffer)) != -1){
			out.write(buffer,0,bytesRead);
		}

		thumbnail.close();
		out.flush();
		out.close();

		try {
			immagineThumbnail = ImageIO.read(new File(destinationThumbnail.toString()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		imageScaled = resizeImage(immagineOriginal, TypeImg(immagineOriginal),immagineThumbnail.getWidth(),immagineThumbnail.getHeight());

		return true;

	}

	private static BufferedImage resizeImage(BufferedImage originalImage, int type, int IMG_WIDTH, int IMG_HEIGHT) {
		BufferedImage resizedImage = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, type);
		Graphics2D g = resizedImage.createGraphics();
		g.drawImage(originalImage, 0, 0, IMG_WIDTH, IMG_HEIGHT, null);
		g.dispose();

		return resizedImage;
	}


	private static double CompareImg(BufferedImage img1, BufferedImage img2){

		int width1 = img1.getWidth();
		int width2 = img2.getWidth();
		int height1 = img1.getHeight();
		int height2 = img2.getHeight();
		double diff = 0.0;

		if ((width1 != width2) || (height1 != height2)) {
			System.err.println("Error: Images dimensions mismatch");
			System.exit(1);
		}

		for (int y = 0; y < height1; y++) {
			for (int x = 0; x < width1; x++) {
				int rgb1 = img1.getRGB(x, y);
				int rgb2 = img2.getRGB(x, y);
				int r1 = (rgb1 >> 16) & 0xff;
				int g1 = (rgb1 >>  8) & 0xff;
				int b1 = (rgb1      ) & 0xff;
				int r2 = (rgb2 >> 16) & 0xff;
				int g2 = (rgb2 >>  8) & 0xff;
				int b2 = (rgb2      ) & 0xff;
				diff += Math.abs(r1 - r2)/255.0;
				diff += Math.abs(g1 - g2)/255.0;
				diff += Math.abs(b1 - b2)/255.0;
			}
		}
		double n = width1 * height1 * 3;
		double p = diff / n;

		return p;
	}


	private static int TypeImg(BufferedImage img){
		int type = img.getType() == 0? BufferedImage.TYPE_INT_ARGB : img.getType();

		return type;
	}


	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		Object source = e.getSource();
		if(((JButton)source).getText().equalsIgnoreCase("Compare")){
			GenericDialog gd = new GenericDialog("Differenza Immagini");

			JLabel lbl = new JLabel("Il valore MSE è di: "+
					(arrotondamento(CompareImg(imageScaled,immagineThumbnail))));
			
			gd.setResizable(false);
			gd.add(lbl);

			gd.showDialog();
		}

		///////////////////////////////////////////////////////////
		//	il commento sottostante serve a creare l'evento		//
		//	al bottone Change Thumbnail	commentato			   //
		//	per dare la possibilità di future implementazioni //
		///////////////////////////////////////////////////////

		/*	if (((JButton)source).getText().equalsIgnoreCase("Change Thumbnail")){

			JFileChooser fileChooser = new JFileChooser();
			FileFilter filter = new FileNameExtensionFilter("JPEG file", "jpg", "jpeg");
			fileChooser.setAcceptAllFileFilterUsed(false);
			fileChooser.setFileFilter(filter);
			if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				File file = fileChooser.getSelectedFile();
				// load from file
			}

		}*/

	}

	class Runner extends Thread { // inner class
		private String command;
		private ImagePlus imp;
		Runner(String command, ImagePlus imp) {
			super(command);
			this.command = command;
			this.imp = imp;
			setPriority(Math.max(getPriority()-2, MIN_PRIORITY));
			start();
		}
	}

	public double arrotondamento(double x){
		x = Math.floor(x*100);
		x = x/100;
		return x;
	}



	public String  fileUrl(String fAddress, String localFileName, String destinationDir) {
		OutputStream outStream = null;
		URLConnection  uCon = null;
		final int size = 1024;
		InputStream is = null;
		String path = "";

		try {
			URL Url;
			byte[] buf;
			int ByteRead, ByteWritten = 0;
			Url= new URL(fAddress);
			outStream = new BufferedOutputStream(new
					FileOutputStream(destinationDir+localFileName));

			uCon = Url.openConnection();
			is = uCon.getInputStream();
			buf = new byte[size];

			while ((ByteRead = is.read(buf)) != -1) {
				outStream.write(buf, 0, ByteRead);
				ByteWritten += ByteRead;
			}
			path = destinationDir+localFileName;
		}catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			try {
				is.close();
				outStream.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		return path;
	}

	public String  fileDownload(String fAddress, String destinationDir)	{
		String path = "";
		int slashIndex = fAddress.lastIndexOf('/');
		int periodIndex = fAddress.lastIndexOf('.');

		String fileName=fAddress.substring(slashIndex + 1);

		if (periodIndex >= 1 &&  slashIndex >= 0 
				&& slashIndex < fAddress.length()-1)
		{
			path = fileUrl(fAddress,fileName,destinationDir);
		}
		else
			System.err.println("path or file name.");

		return path;
	}	
}