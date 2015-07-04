package live.Abhinav.iotapp.app;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import net.sourceforge.zbar.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import de.keyboardsurfer.android.widget.crouton.Configuration;
import de.keyboardsurfer.android.widget.crouton.Crouton;

import de.keyboardsurfer.android.widget.crouton.Style;

import java.util.ArrayList;

import static live.Abhinav.iotapp.app.Keys.*;


public class MainActivity extends Activity implements AdapterProducts.ClickListener, View.OnClickListener {

    /**
     * Crouton
     */
    private static final Style INFINITE = new Style.Builder().
            setBackgroundColorValue(Style.holoBlueLight).build();
    private static final Configuration CONFIGURATION_INFINITE = new Configuration.Builder()
            .setDuration(Configuration.DURATION_INFINITE)
            .build();
    private Crouton infiniteCrouton;

    ArrayList<String> croutonArrayList = new ArrayList<String>();
    //--------end of crouton------------


    //Camera-----------------------start
    FrameLayout cameraPreview;
    private Camera mCamera;
    private CameraPreview mPreview;
    private Handler autoFocusHandler;


    Button scanButton;

    ImageScanner scanner;

    private boolean barcodeScanned = false;
    private boolean previewing = true;


    //Camera-----------------------end

    private AppController volleySingleton;
    private RequestQueue requestQueue;

    //Array list of POJO
    private ArrayList<Product> productArrayList = new ArrayList<Product>();

    //Get a reference to the adapter to be passed to setAdapter() method
    private AdapterProducts adapterProducts;
    //Recycler view
    private RecyclerView recyclerView;


    FragmentManager fragmentManager;

    static {
        System.loadLibrary("iconv");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        volleySingleton = AppController.getInstance();
        requestQueue = volleySingleton.getRequestQueue();


        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        adapterProducts = new AdapterProducts(getApplicationContext());
        adapterProducts.setClickListener(this);
        cameraPreview = (FrameLayout) findViewById(R.id.cameraPreview);
        cameraPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(barcodeScanned) {
                    barcodeScanned=false;

                    mCamera.setPreviewCallback(previewCb);
                    mCamera.startPreview();
                    previewing=true;
                }
            }
        });
        recyclerView.setAdapter(adapterProducts);

        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

        /*productArrayList.add(new Product(false,"1","Apple"));
        productArrayList.add(new Product(false,"1","Banana"));
        productArrayList.add(new Product(true, "1", "Potato"));
        productArrayList.add(new Product(false, "1", "Petrol"));
        productArrayList.add(new Product(false, "1", "Nutrela"));
        adapterProducts.setProductArrayList(productArrayList);*/
//        prepareCamera();
        sendJsonRequest();

    }



    private void sendJsonRequest() {
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET,
                "http://ecomxebia.esy.es/list.php",
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        Log.d("Lifecycle3", response.toString());
                        productArrayList = parseJSONResponse(response);
                        adapterProducts.setProductArrayList(productArrayList);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        Log.d("Lifecycle4", volleyError.toString());
                    }
                });
        requestQueue.add(request);
    }

    private ArrayList<Product> parseJSONResponse(JSONArray response) {
        ArrayList<Product> listTransactions = new ArrayList<Product>();
        if (response != null && response.length() > 0) {
            try {
                StringBuilder data = new StringBuilder();
                JSONArray jsonArray = response;
                Log.d("Lifecycle1", String.valueOf(response.length()));
                for (int i = 0; i < jsonArray.length(); i++) {
                    Log.d("Lifecycle", "Inside loop");

                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String tSNo = jsonObject.getString(KEY_SNO);
                    String tProductName = jsonObject.getString(KEY_PNAME);

                    data.append(tSNo + " " + tProductName + " " + "\n");

                    Product product = new Product();
                    product.setIsChecked(false);
                    product.setpName(tProductName);
                    product.setpSNo(tSNo);

                    listTransactions.add(product);
                    Log.d("Lifecycle", "Inside loop");
                }
                Log.d("Lifecycle2", listTransactions.toString());
            } catch (JSONException e) {
                Log.d("Lifecycle", "Inside JSON EXCEPTION: " + e);
            }
        }
        return listTransactions;
    }

    @Override
    public void itemClicked(View view, int position) {
//        Toast.makeText(this, "Position " + position, Toast.LENGTH_SHORT).show();
        showCrouton("Position=" + position);
        recyclerView.scrollToPosition(0);
    }

    /**
     * Camera specific methods
     * -------------start-----------------
     */
    public void prepareCamera() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        autoFocusHandler = new Handler();
        mCamera = getCameraInstance();

        /* Instance barcode scanner */
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        mPreview = new CameraPreview(this, mCamera, previewCb, autoFocusCB);

        cameraPreview.addView(mPreview);

    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e) {
        }
        return c;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mPreview.getHolder().removeCallback(mPreview);
            mCamera.release();
            mCamera = null;
            cameraPreview.removeView(mPreview);
        }
    }

    private Runnable doAutoFocus = new Runnable() {
        public void run() {
            if (previewing)
                mCamera.autoFocus(autoFocusCB);
        }
    };

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            Image barcode = new Image(size.width, size.height, "Y800");
            barcode.setData(data);

            int result = scanner.scanImage(barcode);

            if (result != 0) {
                previewing = false;
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();

                SymbolSet syms = scanner.getResults();
                for (Symbol sym : syms) {
                    // scanText.setText("barcode result " + sym.getData());
                    Toast.makeText(getApplicationContext(), sym.getData(), Toast.LENGTH_SHORT).show();
                    adapterProducts.test("63000");
                    barcodeScanned = true;
                }
            }
        }
    };

    // Mimic continuous auto-focusing
    Camera.AutoFocusCallback autoFocusCB = new Camera.AutoFocusCallback() {
        public void onAutoFocus(boolean success, Camera camera) {
            autoFocusHandler.postDelayed(doAutoFocus, 1000);
        }
    };
/**
 * Camera specific methods
 * -------------end-----------------
 */


    /**
     * Crouton COde
     */
    private void showCrouton(String croutonText) {
        croutonArrayList.add(croutonText);
        Crouton.cancelAllCroutons();

        final Crouton crouton;
        crouton = Crouton.makeText(this, croutonArrayList.get(croutonArrayList.size() - 1), INFINITE);
//        infiniteCrouton = crouton;
        crouton.setOnClickListener(this).setConfiguration(CONFIGURATION_INFINITE).show();
    }

    @Override
    public void onClick(View v) {
        Toast.makeText(this, "Item Closed", Toast.LENGTH_LONG).show();
        removeCrouton();
    }

    public void removeCrouton() {

        if (croutonArrayList.size() > 0) {
            croutonArrayList.remove(croutonArrayList.size() - 1);
            Crouton.cancelAllCroutons();
            if (croutonArrayList.size() > 0) {
                Crouton crouton;
                crouton = Crouton.makeText(this, croutonArrayList.get(croutonArrayList.size() - 1), INFINITE);
                crouton.setOnClickListener(this).setConfiguration(CONFIGURATION_INFINITE).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("Anand", "onDestroy");

        Crouton.cancelAllCroutons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("Anand","onResume");
        prepareCamera();
    }
    @Override
    protected void onPause() {
        super.onPause();
        Log.d("Anand", "onPause");
        releaseCamera();
    }
}