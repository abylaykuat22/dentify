import { createFileRoute, Outlet } from '@tanstack/react-router'

import { guardSection, sectionHead } from '@/app/guards'
import { SectionError } from '@/app/shell/SectionError'

// Раздел «patients»: право проверяется в beforeLoad, «нет доступа» рисует errorComponent
// внутри каркаса (заголовок вкладки — sectionHead); вкладки — дочерние маршруты в папке patients/.
export const Route = createFileRoute('/_app/patients')({
  // Компонент лейаута — просто Outlet (он уже в начальной загрузке): отдельный чанк ради него — лишний
  // запрос в цепочке первого экрана (PLT-22). В чанк уходит только «нет доступа».
  codeSplitGroupings: [['errorComponent']],
  beforeLoad: guardSection('patients'),
  head: sectionHead,
  errorComponent: SectionError,
  component: Outlet,
})
