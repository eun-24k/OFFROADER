package com.mit.offroader.ui.fragment.map

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.mit.offroader.R
import com.mit.offroader.BuildConfig
import com.mit.offroader.databinding.FragmentSanMapBinding
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapFragment
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.UiSettings
import com.naver.maps.map.overlay.Align
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.util.MarkerIcons
import com.naver.maps.map.widget.CompassView
import com.naver.maps.map.widget.ScaleBarView
import com.naver.maps.map.widget.ZoomControlView
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale


class SanMapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        fun newInstance() = SanMapFragment()
        private const val PERMISSION_REQUEST_CODE = 100
        private val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private var _binding: FragmentSanMapBinding? = null
    private val binding get() = _binding!!

    private val sanMapViewModel by viewModels<SanMapViewModel>()

    private lateinit var mapView: MapView
    private lateinit var naverMap: NaverMap
    private lateinit var locationSource: FusedLocationSource
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var uiSettings: UiSettings

    private val firestore = FirebaseFirestore.getInstance()

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                initMapView()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentSanMapBinding.inflate(inflater, container, false)
        NaverMapSdk.getInstance(requireContext()).client =
            NaverMapSdk.NaverCloudPlatformClient(BuildConfig.NAVERMAPS_API_KEY)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)

        if (!hasPermission()) {
            requestMultiplePermissions.launch(PERMISSIONS)
        } else {
            initMapView()
        }
    }

    // 프래그먼트에 지도 추가
    private fun initMapView() {
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map_view) as MapFragment?
                ?: MapFragment.newInstance().also {
                    childFragmentManager.beginTransaction().add(R.id.map_view, it)
                        .commit()
                }

        // fragment의 getMapAsync() 메서드로 OnMapReadyCallBack 콜백을 등록하면, 비동기로 NaverMap 객체를 얻을 수 있음
        mapFragment.getMapAsync(this)
        locationSource = FusedLocationSource(this, PERMISSION_REQUEST_CODE)
    }

    // hasPermission()에서는 위치 권한 있을 경우 true, 없을 경우 false
    private fun hasPermission(): Boolean {
        for (permission in PERMISSIONS) {
            if (activity?.let { ContextCompat.checkSelfPermission(it, permission) }
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    // 지도 그리는 부분
    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap
        // 지도 타입 설정
        this.naverMap.mapType = NaverMap.MapType.Basic
        // 지도 레이어 설정
        this.naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_MOUNTAIN, true)
        // 지도 컨트롤 요소 수동 설정
        this.uiSettings = naverMap.uiSettings
        uiSettings.isZoomControlEnabled = false
        uiSettings.isCompassEnabled = true
        uiSettings.isScaleBarEnabled = false

        val zoomControlView: ZoomControlView = binding.zoom
        zoomControlView.map = naverMap

        val compassView: CompassView = binding.compass
        compassView.map = naverMap

        val scaleBarView: ScaleBarView = binding.scalebar
        scaleBarView.map = naverMap

        // 최대 확대 및 축소 비율 설정
        this.naverMap.maxZoom = 19.0
        this.naverMap.minZoom = 10.0

        setUpMap()

        //권한 확인하여 위치 추가
        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(requireContext()) // 초기화
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // 마커 정보를 담을 배열 설정
        var markerDTOs: ArrayList<MarkerDTO> = arrayListOf()
        // Firestore에서 markers collection 접근하여 쿼리를 가져옴
        firestore.collection("markers")
            .addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                if (querySnapshot == null) {
                    return@addSnapshotListener
                }
                if (markerDTOs.size == 0) {
                    for (snapshot in querySnapshot.documents) {
                        markerDTOs.add(snapshot.toObject(MarkerDTO::class.java)!!)
                        for (idx in 0 until markerDTOs.size) {
                            // 마커 여러개 찍기
                            val markers = arrayOfNulls<Marker>(markerDTOs.size)
                            markers[idx] = Marker()
                            val lat = markerDTOs[idx].lat
                            val lnt = markerDTOs[idx].lng
                            markers[idx]!!.position = LatLng(lat!!, lnt!!)
                            markers[idx]!!.captionText = markerDTOs[idx].name!!
                            markers[idx]!!.icon =
                                OverlayImage.fromResource(R.drawable.ic_marker)
                            markers[idx]!!.width = resources.getDimensionPixelSize(R.dimen.marker_size_3)
                            markers[idx]!!.height = resources.getDimensionPixelSize(R.dimen.marker_size_3)
                            //카메라 변화 감지하여 줌 레벨에 따라 마커의 크기 변경
                            naverMap.addOnCameraChangeListener { _, _ ->
                                when (naverMap.cameraPosition.zoom) {
                                    10.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_1)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_1)
                                    }
                                    11.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_2)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_2)
                                    }
                                    12.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_3)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_3)
                                    }
                                    13.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_4)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_4)
                                    }
                                    14.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_5)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_5)
                                    }
                                    15.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_6)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_6)
                                    }
                                    16.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_7)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_7)
                                    }
                                    17.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_8)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_8)
                                    }
                                    18.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_9)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_9)
                                    }
                                    19.0 -> {
                                        markers[idx]!!.width =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_10)
                                        markers[idx]!!.height =
                                            resources.getDimensionPixelSize(R.dimen.marker_size_10)
                                    }
                                }
                            }
                            markers[idx]!!.isIconPerspectiveEnabled = true
                            markers[idx]!!.captionColor = Color.WHITE
                            markers[idx]!!.captionHaloColor = Color.rgb(0, 0, 0)
                            markers[idx]!!.captionTextSize = 16f
                            //마커 클릭 시 정보창 visibility 유무
                            markers[idx]!!.setOnClickListener {
                                if (binding.markerInfo.visibility == View.GONE) {
                                    binding.tvMarkerName.text = markerDTOs[idx].name
                                    binding.tvMarkerHeight.text = NumberFormat.getInstance(Locale.getDefault()).format(markerDTOs[idx].height)+"m"
                                    binding.tvMarkerDescription.text = markerDTOs[idx].description
                                    binding.markerInfo.visibility = View.VISIBLE
                                } else if (binding.markerInfo.visibility == View.VISIBLE) {
                                    binding.markerInfo.visibility = View.GONE
                                }
                                false
                            }
                            markers[idx]!!.map = naverMap
                        }
                    }
                }

                binding.etInputLocation.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        binding.ivSearchLocation.performClick()
                        binding.ivSearchLocation.setOnClickListener {
                            for (idx in 0 until markerDTOs.size) {
                                if (binding.etInputLocation.text.toString() == markerDTOs[idx].name) {
                                    val cameraUpdate = CameraUpdate.scrollAndZoomTo(
                                        LatLng(markerDTOs[idx].lat!!, markerDTOs[idx].lng!!), 15.0
                                    )
                                    naverMap.moveCamera(cameraUpdate)
                                }
                            }
                        }
                    }
                    false
                }
                binding.ivSearchLocation.setOnClickListener {
                    hideKeyboard()
                    for (idx in 0 until markerDTOs.size) {
                        if (binding.etInputLocation.text.toString() == markerDTOs[idx].name) {
                            val cameraUpdate = CameraUpdate.scrollAndZoomTo(
                                LatLng(markerDTOs[idx].lat!!, markerDTOs[idx].lng!!), 17.0
                            ).animate(CameraAnimation.Fly, 1000)
                            naverMap.moveCamera(cameraUpdate)
                        }
                    }
                }
            }
    }
    // 키보드 내림처리
    private fun hideKeyboard() {
        if (activity != null && activity?.currentFocus != null) {
            // 프래그먼트기 때문에 getActivity() 사용
            val inputManager =
                requireActivity().getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.hideSoftInputFromWindow(
                requireActivity().currentFocus!!.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
    }

    // 현재 위치 기능 지도에 추가
    private fun setUpMap() {
        naverMap.locationSource = locationSource //현위치
        naverMap.uiSettings.isLocationButtonEnabled = true // 현 위치 버튼 기능
        naverMap.locationTrackingMode = LocationTrackingMode.Follow // 위치를 추적하면서 카메라도 같이 움직임
        // 줌
        naverMap.maxZoom = 15.0  // (최대 21)
        naverMap.minZoom = 9.0
    }

    // MapView 라이프 사이클 메서드를 호출
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

}