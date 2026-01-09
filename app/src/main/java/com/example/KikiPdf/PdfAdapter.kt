package com.kikipdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView

class PdfAdapter(private val renderer: PdfRenderer, private val nightMode: Boolean) :
    RecyclerView.Adapter<PdfAdapter.PageViewHolder>() {

    class PageViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val photoView = PhotoView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setPadding(0, 0, 0, 10)
            
            if (nightMode) {
               val matrix = floatArrayOf(
                   -1f, 0f, 0f, 0f, 255f,
                   0f, -1f, 0f, 0f, 255f,
                   0f, 0f, -1f, 0f, 255f,
                   0f, 0f, 0f, 1f, 0f
               )
               colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
           }
        }
        return PageViewHolder(photoView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = renderer.openPage(position)
        

        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        
        holder.photoView.setImageBitmap(bitmap)
        holder.photoView.maximumScale = 5.0f
        holder.photoView.mediumScale = 2.5f
        

        
        page.close()
    }

    override fun getItemCount(): Int = renderer.pageCount
}
