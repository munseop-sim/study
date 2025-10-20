### Manual
https://mapstruct.org/documentation/stable/reference/html/
---

### MAPSTRUCT 전략
1. MAPSTRUCT는 target, source 클래스의 프로퍼티 타입, 프로퍼티명이 일치하면 관련 클래스로의 변환되는 코드를 자동으로 생성 해준다. 
1. 유사한 lib인 ModelMapper는 별도의 코드를 생성하지 않고 runtime시 reflection 기능을 사용하여 클래스를 변환해준다. 따라서 성능의 문제가 야기될 수도 있음)
1. DTO 클래스내에 of를 선언을 지양하고, 가급적 Mapper에 선언된 메소드를 사용한다.  
1. 엔티티 혹은 DTO 클래스 변경시에 관련 MapStruct를 이용한 Interface 혹은 Class를 Reload하여 Generated된 코드를 확인한다.
1. 엔티티 Mapper, DTO Mapper는 각각 다른 패키지에 위치하도록 한다.
1. 하나의 클래스내에 inner 클래스, 인터페이스 형태로 Mapper를 작성한다. (실제 파일 Generated시에 각각의 파일로 생성됨)
1. 클래스의 크기가 거대해지면 mapper 패키지를 별도로 생성해서 해당 Mapper클래스를 위치하도록 한다.
--- 

### enum 타입 매핑
- enum타입의 경우 default로 .name을 호출해서 매핑한다.
- entity 내에는 enum타입으로 프로퍼티가 정의되어 있고, DTO에는 해당 enum타입이 string으로 정의되어 있는 경우 mapping시에 entity.enum타입.name() 을 호출해서 매핑한다.

--- 

### Mapper 선언

#### interface 선언
```
@Mapper(
        injectionStrategy = InjectionStrategy.CONSTRUCTOR
    )
    public interface ProductEditMapper{
        // ProductEditMapper INSTANCE = Mappers.getMapper( ProductEditMapper.class );
        ...
        ...  
    }

```
- mapper annotation내의 componentModel= "spring" 선언되면 호출시에 'ProductEditMapper.메소드명'으로 호출가능
- mapper annotation내의 componentModel= "spring" 선언이 생략되면 'ProductEditMapper.INSTANCE.메소드명'으로 호출
- interface 선언시에 매핑 전처리(@BeforeMapping),후처리(@AfterMapping) 불가
### abstract class 선언
```
 @Mapper
    public abstract static class ProductRandomScheduleMapper{
        public static final ProductRandomScheduleMapper INSTANCE = Mappers.getMapper( ProductRandomScheduleMapper.class );
        ...
        ...  
    }

```



#### 빈으로 등록 
```
@Mapper(
    componentModel = "spring",
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
```
위와 같이 Mapper를 정의하면 자동 생성된 클래스의 @Component annotation이 추가됨을 알 수 있다.

#### 빈으로 등록하지 않는 방법 - componentModel 생략
```
@Mapper(
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
```
Generate된 클래스를 살펴보면 @component annotation이 없음을 확인 할 수 있다.  

출처 - https://mapstruct.org/documentation/stable/reference/html/#configuration-options
![image](uploads/2ee645cb6869696ccc29815e359cf826/image.png)

--- 


### @Mapping
```
        @Mapping(target = "itemPriceUnitCaption", source = "itemPriceUnit.description")
        @Mapping(target = "itemGubunCdCaption", source = "itemGubunCd.description")
        @Mapping(target = "itemStockUnitCaption", source = "itemStockUnit.description")
        @Mapping(target = "itemCheckGubunCaption", source = "itemCheckGubun.description")
        @Mapping(target = "itemStocks", ignore = true )
        @Mapping(target = "productItemSubinfo", ignore = true )
        @Mapping(target = "supplierMappings", ignore = true )
        @Mapping(target = "tagMappings", ignore = true )
        @Mapping(target = "itemImages", ignore = true )
        ProductItemDTO of(ProductItem productItem1);

패키지 com.atd.admin.app.earth.product.domain.dto  
클래스 ProductDtoMapper
```
- @Mapping(target = "itemPriceUnitCaption", source = "itemPriceUnit.description") : DTO에는  String itemPriceUnitCaption 선언되어 있고 Entity에는 EnumType itemPriceUnit으로 선언되어 있는경우 
- @Mapping(target = "itemStocks", ignore = true ) :  DTO에는 프로퍼티 itemStocks와 관계된 프로퍼티가 없을 경우, 또는 관련 매핑을 무시하고 싶을 때 사용


### @AfterMapping, @BeforeMapping
매핑후 처리
```
// 해당 mapper를 abstract 클래스로 선언하고, 메소드를 producted로 선언하면 Generated되는 코드에 관련 사항이 적용됨을 확인 할 수 있다.

  @AfterMapping
        protected void afterGroupMapping(@MappingTarget List<RandomProductGroupDTO> groupList){
            groupList.sort(Comparator.comparing(t->t.getRpgTitle()));
        }
```


### 유용한 기능

#### 객체 합치기
```
@Mapper
public interface AddressMapper {

    @Mapping(source = "person.description", target = "description")
    @Mapping(source = "address.houseNo", target = "houseNumber")
    DeliveryAddressDto personAndAddressToDeliveryAddressDto(Person person, Address address);
}
```
- 여러 소스 객체가 동일한 이름의 속성을 정의하는 경우 @Mapping 설정
- 그 외에 속성들은 자동으로 매핑된다.

#### 기존 빈 인스턴스 업데이트
```
@Mapper
public interface CarMapper {

    void updateCarFromDto(CarDto carDto, @MappingTarget Car car);
}
```
- 전달된 Car 인스턴스를 지정된 CarDto 개체의 속성으로 업데이트 한다.
- 매핑 대상으로 표시된 매개변수는 하나만 있을 수 있다.
- void 대신 반환 유형을 대상 매개변수의 유형으로 설정할 수도 있음. 그러면 전달된 매핑 대상을 업데이트하고 이를 반환한다.

#### 커스텀 매퍼
```
@Mapper
public interface UserBodyValuesMapper {
    UserBodyValuesMapper INSTANCE = Mappers.getMapper(UserBodyValuesMapper.class);
    
    @Mapping(source = "inch", target = "centimeter", qualifiedByName = "inchToCentimeter")
    public UserBodyValues userBodyValuesMapper(UserBodyImperialValuesDTO dto);
    
    @Named("inchToCentimeter") 
    public static double inchToCentimeter(int inch) { 
        return inch * 2.54; 
    }
}
```
- @Named 어노테이션을 이용하여 커스텀 메소드를 만든다.
- @Mapping 어노테이션의 qualifiedByName 속성안에 입력하여 메소드를 호출한다.

---

#### Mapstruct와 lombok
Mapstruct와 lombok을 같이 사용하려면 annotationProcessorPaths 에 다음 사항을 추가해야 한다.
1. lombok dependency
1. lombok-mapstruct-binding dependency
- https://www.baeldung.com/mapstruct#Conclusion
- https://projectlombok.org/changelog (v1.18.16)


