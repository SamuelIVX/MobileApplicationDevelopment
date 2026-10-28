package com.example.slideshow.data

import com.example.slideshow.model.Spidermen
import com.example.slideshow.R

class Datasource {
    fun loadSpidermenImages(): List<Spidermen>{
        return listOf<Spidermen>(
            Spidermen(R.string.caption_1, R.drawable.spiderman1),
            Spidermen(R.string.caption_2, R.drawable.spiderman2),
            Spidermen(R.string.caption_3, R.drawable.spiderman3),
            Spidermen(R.string.caption_4, R.drawable.spiderman4),
            Spidermen(R.string.caption_5, R.drawable.spiderman5),
            Spidermen(R.string.caption_6, R.drawable.spiderman6),
            Spidermen(R.string.caption_7, R.drawable.spiderman7),
            Spidermen(R.string.caption_8, R.drawable.spiderman8),
            Spidermen(R.string.caption_9, R.drawable.spiderman9),
            Spidermen(R.string.caption_10, R.drawable.spiderman10)
        )
    }
}