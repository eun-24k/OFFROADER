package com.mit.offroader.activity.main

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mit.offroader.R
import com.mit.offroader.databinding.ActivityMainBinding
import com.mit.offroader.fragment.chatbot.ChatBotFragment
import com.mit.offroader.fragment.home.HomeFragment
import com.mit.offroader.fragment.map.MapFragment
import com.mit.offroader.fragment.mydetail.MyDetailFragment
import com.mit.offroader.fragment.sanlist.SanListFragment

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val mainViewModel by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // 앱 초기 실행 시 홈화면으로 설정
        if (savedInstanceState == null) {
            binding.bottomNavigationView.selectedItemId = R.id.fragment_home
        }

        initView()
    }
    // 초기 화면에서 실행되는 함수 모음.
    private fun initView() {

        bottomNavigationListener()
    }

    private fun initObservers() = with(mainViewModel){
    }

    private fun bottomNavigationListener() = with(binding){
        bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.fragment_home -> {
                    supportFragmentManager.beginTransaction().replace(R.id.main_container, HomeFragment()).commit()
                    true
                }
                R.id.fragment_san -> {
                    supportFragmentManager.beginTransaction().replace(R.id.main_container, SanListFragment()).commit()
                    true
                }
                R.id.fragment_map -> {
                    supportFragmentManager.beginTransaction().replace(R.id.main_container, MapFragment()).commit()
                    true
                }
                R.id.fragment_chatbot -> {
                    supportFragmentManager.beginTransaction().replace(R.id.main_container, ChatBotFragment()).commit()
                    true
                }
                R.id.fragment_my_detail -> {
                    supportFragmentManager.beginTransaction().replace(R.id.main_container, MyDetailFragment()).commit()
                    true
                }
                else -> false
            }
        }

    }



}