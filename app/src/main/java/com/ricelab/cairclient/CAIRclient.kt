import android.util.Log
import android.widget.TextView
import com.aldebaran.qi.sdk.QiContext

class CAIRclient(private val textView: TextView) {

    fun startLoop() {
        var loopCount = 1
        while (true) {
            Log.d("CAIRclient", "Loop count: $loopCount") // Log loop count to ensure it’s running

            // Update the TextView on the UI thread
            textView.post {
                textView.text = "Hello $loopCount"
                Log.d("CAIRclient", "Updated TextView with Hello $loopCount") // Log UI update
            }

            // Increment the loop counter
            loopCount++

            // Sleep for 2 seconds
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                break // Exit loop if thread is interrupted
            }
        }
    }
}