import { Tabs as TabsPrimitive } from '@base-ui/react/tabs'
import type { VariantProps } from 'class-variance-authority'

import { keepSelectedInView } from '@/shared/lib/keepSelectedInView'
import { cn } from '@/shared/lib/utils'

import { pillTabsListVariants, pillTabVariants } from './tabs-variants'

/**
 * Вкладки-пилюли «Кресла» (shadcn Tabs на Base UI) — внутри экрана. Виды пилюль и контейнера
 * (`pillTabsListVariants`, `pillTabVariants`, варианты `surface` и `size`) — в tabs-variants.ts:
 * их же берут вкладки-маршруты раздела (`PillTabs` каркаса).
 */
function Tabs({ className, orientation = 'horizontal', ...props }: TabsPrimitive.Root.Props) {
  return (
    <TabsPrimitive.Root
      data-slot="tabs"
      data-orientation={orientation}
      className={cn('group/tabs flex gap-3 data-horizontal:flex-col', className)}
      {...props}
    />
  )
}

/**
 * Полоса вкладок. Не влезает (телефон) — прокручивается вбок без полосы прокрутки, а выбранная
 * вкладка подъезжает в видимую область (`keepSelectedInView`; ref поэтому занят — не передаётся).
 */
function TabsList({
  className,
  surface,
  ...props
}: Omit<TabsPrimitive.List.Props, 'ref'> & VariantProps<typeof pillTabsListVariants>) {
  return (
    <TabsPrimitive.List
      ref={keepSelectedInView}
      data-slot="tabs-list"
      className={cn(
        pillTabsListVariants({ surface }),
        'max-w-full overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden',
        className,
      )}
      {...props}
    />
  )
}

function TabsTrigger({ className, size, ...props }: TabsPrimitive.Tab.Props & VariantProps<typeof pillTabVariants>) {
  return <TabsPrimitive.Tab data-slot="tabs-trigger" className={cn(pillTabVariants({ size }), className)} {...props} />
}

function TabsContent({ className, ...props }: TabsPrimitive.Panel.Props) {
  return <TabsPrimitive.Panel data-slot="tabs-content" className={cn('flex-1', className)} {...props} />
}

export { pillTabsListVariants, pillTabVariants, Tabs, TabsContent, TabsList, TabsTrigger }
