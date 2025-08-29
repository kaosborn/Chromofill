package kaosborn.chromafill
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator.*
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import kaosborn.chromafill.databinding.FragmentChromafillBinding

class ChromafillFragment : Fragment() {
    private var _binding:FragmentChromafillBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm:GridGamesViewModel

    override fun onCreateView (inflater:LayoutInflater, container:ViewGroup?, savedInstanceState:Bundle?): View {
        _binding = FragmentChromafillBinding.inflate (inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        vm = ViewModelProvider (requireActivity()).get (GridGamesViewModel::class.java)
        binding.pvm = vm
        return binding.root
    }

    override fun onViewCreated (view:View, state:Bundle?) {
        super.onViewCreated (view, state)

        requireActivity().addMenuProvider (ChromafillMenuProvider(view,vm), viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.buttonReset.setOnClickListener {
            vm.initGame()
            vm.colorChoiceValue = if (! vm.isGameActiveValue) null else vm.at(vm.xRoot,vm.yRoot)
        }

        binding.palette.addOnButtonCheckedListener { group, checkedId, isChecked ->
            var w: Button? = null
            var ix = -1
            for (v in group.children) {
                ix++
                if (v.id == checkedId) {
                    w = v as Button
                    break
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.width = (resources.getDimension(R.dimen.paletteTileSize) + 0.5F).toInt()
            lp.height = (resources.getDimension(R.dimen.paletteTileSize) * (if (isChecked) 0.75F else 1.0F) + 0.5F).toInt()
            w!!.layoutParams = lp
            if (isChecked)
                vm.colorChoiceValue = ix
        }

        if (! vm.isGame())
            vm.initGame()
        else {
            makeBoard()
            makePalette (vm.at(vm.xRoot,vm.yRoot))
        }

        vm.isGameActive.observe (viewLifecycleOwner) { newVal -> onGameActiveChange(newVal) }
        vm.colorChoice.observe (viewLifecycleOwner) { newVal -> onColorChoiceChange(newVal) }
    }

    private fun makePalette (selectedIx:Int?) {
        binding.palette.removeAllViews()
        var selectedButton:MaterialButton? = null
        for (i in 0..<vm.gameColors.size) {
            val v = MaterialButton (requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            if (i==selectedIx)
                selectedButton = v
            v.textSize = 30.0F
            v.setTypeface (v.typeface, Typeface.BOLD)
            v.setPadding (0,0,0,0)
            v.setTextColor (Color.BLACK)
            v.setBackgroundColor (vm.gameColors[i])
            val lp = LinearLayout.LayoutParams (LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.width = (resources.getDimension(R.dimen.paletteTileSize)+0.5F).toInt()
            lp.height = (resources.getDimension(R.dimen.paletteTileSize)*(if(i==selectedIx)0.75F else 1.0F)+0.5F).toInt()
            v.layoutParams = lp
            binding.palette.addView (v)
        }
        if (selectedButton!=null)
            selectedButton.isChecked = true
    }

    private fun makeBoard() {
        binding.board.removeAllViews()
        for (y in 0..<vm.dataHeight())
            for (x in 0..<vm.dataWidth(y)) {
                val v = TextView(requireContext())
                v.setTextColor (ContextCompat.getColor(requireContext(),R.color.black))
                v.setBackgroundColor (vm.gameColors[vm.at(x,y)%vm.gameColors.size])
                v.id = View.generateViewId()
                v.width = (resources.getDimension(R.dimen.cellTileSize)+0.5F).toInt()
                v.height = (resources.getDimension(R.dimen.cellTileSize)+0.5F).toInt()
                v.gravity = Gravity.CENTER

                val p = GridLayout.LayoutParams(GridLayout.spec(y+3),GridLayout.spec(x))
                if (x>0) p.leftMargin = (resources.getDimension(R.dimen.cellTileMargin)+0.5F).toInt()
                if (y>0) p.topMargin = (resources.getDimension(R.dimen.cellTileMargin)+0.5F).toInt()
                v.layoutParams = p
                binding.board.addView (v)
            }
    }

    private fun repaintBoard (oldColor:Int) {
        var i = 0
        for (y in 0..<vm.dataHeight())
            for (x in 0..<vm.dataWidth(y)) {
                val w = binding.board.getChildAt(i) as TextView
                if (vm.rankAt(x,y)!=0) {
                    val oldLevel = vm.gameColors[oldColor]
                    val newLevel = vm.gameColors[vm.at(x,y)%vm.gameColors.size]
                    if (! vm.isContact(x,y)) {
                        val a2 = ofInt(
                            w,
                            "backgroundColor",
                            oldLevel,newLevel)
                        a2.startDelay = (vm.rankAt(x,y)*3).toLong()
                        a2.duration = 25
                        a2.setEvaluator (ArgbEvaluator())
                        a2.start()
                    }
                    else {
                        val a7 = ofInt(0,3)
                        a7.startDelay = (vm.rankAt(x,y)*3).toLong()
                        a7.addUpdateListener {
                            val q = it.animatedValue as Int
                            if (q==0 || q==2)
                                w.text = "$"
                            else if (q==1)
                                w.text = "\$\$"
                            else
                                w.text = ""
                        }
                        a7.start()
                    }
                }
                i++
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onColorChoiceChange (selectedColorIx:Int?) {
        if (vm.isGameActive.value==false || selectedColorIx==null)
            paintBanner()
        else {
            val targetColor = vm.at(vm.xRoot,vm.yRoot)
            if (targetColor!=selectedColorIx) {
                vm.fill (selectedColorIx)
                repaintBoard (targetColor)
                if (vm.isMonochrome())
                    vm.isGameActiveValue = false
            }
        }
    }

    private fun onGameActiveChange (isActive:Boolean?) {
        makeBoard()
        makePalette (vm.at(vm.xRoot,vm.yRoot))
        binding.palette.isEnabled = isActive==true
        if (isActive!=true)
            if (vm.isMonochrome())
                paintBanner()
            else
                vm.colorChoiceValue = null
    }

    private fun paintBanner() {
        val banner = if (vm.isMonochrome()) "WINNER" else "LOSER "
        val maxItems = binding.palette.children.count()
        for ((i,v) in binding.palette.children.withIndex()) {
            if (i>=maxItems)
                break
            if (v is Button)
                (v as MaterialButton).text = banner[i%banner.length].toString()
        }
    }
}
