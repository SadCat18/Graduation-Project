import * as echarts from 'echarts/core'
import { FunnelChart, LineChart, PieChart } from 'echarts/charts'
import {
  DatasetComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  DatasetComponent,
  GraphicComponent,
  FunnelChart,
  GridComponent,
  LegendComponent,
  LineChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer
])

export default echarts
