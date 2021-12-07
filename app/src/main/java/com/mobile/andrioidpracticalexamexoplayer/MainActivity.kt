package com.mobile.andrioidpracticalexamexoplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mobile.andrioidpracticalexamexoplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
    }
}