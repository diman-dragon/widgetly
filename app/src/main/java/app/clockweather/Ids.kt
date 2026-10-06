package app.clockweather

/** ID ячеек суточного прогноза: [колонка, день, иконка, макс, мин]. */
val FC = arrayOf(
    intArrayOf(R.id.d0, R.id.d0n, R.id.d0i, R.id.d0h, R.id.d0l),
    intArrayOf(R.id.d1, R.id.d1n, R.id.d1i, R.id.d1h, R.id.d1l),
    intArrayOf(R.id.d2, R.id.d2n, R.id.d2i, R.id.d2h, R.id.d2l),
    intArrayOf(R.id.d3, R.id.d3n, R.id.d3i, R.id.d3h, R.id.d3l),
    intArrayOf(R.id.d4, R.id.d4n, R.id.d4i, R.id.d4h, R.id.d4l),
    intArrayOf(R.id.d5, R.id.d5n, R.id.d5i, R.id.d5h, R.id.d5l)
)

/** ID почасовых столбиков: [колонка, время, температура, блок]. */
val HR = arrayOf(
    intArrayOf(R.id.h0, R.id.h0t, R.id.h0v, R.id.h0b),
    intArrayOf(R.id.h1, R.id.h1t, R.id.h1v, R.id.h1b),
    intArrayOf(R.id.h2, R.id.h2t, R.id.h2v, R.id.h2b),
    intArrayOf(R.id.h3, R.id.h3t, R.id.h3v, R.id.h3b),
    intArrayOf(R.id.h4, R.id.h4t, R.id.h4v, R.id.h4b),
    intArrayOf(R.id.h5, R.id.h5t, R.id.h5v, R.id.h5b),
    intArrayOf(R.id.h6, R.id.h6t, R.id.h6v, R.id.h6b),
    intArrayOf(R.id.h7, R.id.h7t, R.id.h7v, R.id.h7b)
)

const val HR_MAX = 8

/** «Сцена» — вертикальный почерк температуры по часам (ImageView + scaleType matrix). */
val SCENE_F = intArrayOf(R.drawable.scene_f_1, R.drawable.scene_f_2, R.drawable.scene_f_3, R.drawable.scene_f_4)
val SCENE_L = intArrayOf(R.drawable.scene_l_1, R.drawable.scene_l_2, R.drawable.scene_l_3, R.drawable.scene_l_4)
